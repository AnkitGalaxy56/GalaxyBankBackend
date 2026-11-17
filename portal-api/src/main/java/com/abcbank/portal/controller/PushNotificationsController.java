package com.abcbank.portal.controller;

import com.abcbank.portal.requests.ApiException;
import com.abcbank.portal.requests.AuditLog;
import com.abcbank.portal.requests.Caller;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Push notification campaigns (table MB003003).
 * "Sending" copies the message into the customers' inbox table MB003001, which the mobile app reads.
 *  - Administrators can target any branch; staff are limited to their own branch.
 *  - Sent notifications cannot be edited. Drafts and scheduled ones can, and can be sent now.
 *  - Scheduled notifications are delivered by a job that runs every minute.
 */
@RestController
@RequestMapping("/mobile-utilities/notifications")
public class PushNotificationsController {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationsController.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter SHOWN = DateTimeFormatter.ofPattern("dd MMM, HH:mm", Locale.ENGLISH);
    private static final String ALL_AUDIENCES = "All customers";
    private static final String PENDING_MPIN = "Customers with pending MPIN";
    private static final String ACTIVE_LOANS = "Customers with active loans";
    private static final Set<String> AUDIENCES = Set.of(ALL_AUDIENCES, PENDING_MPIN, ACTIVE_LOANS);

    private final JdbcTemplate db;
    private final AuditLog audit;
    private final TransactionTemplate tx;
    private volatile Boolean tableReady;

    public PushNotificationsController(JdbcTemplate db, AuditLog audit, PlatformTransactionManager tm) {
        this.db = db;
        this.audit = audit;
        this.tx = new TransactionTemplate(tm);
    }

    private static final String SELECT = "select n.*, b.branch_name from public.\"MB003003\" n left join public.\"MB002002\" b on b.branch_code = n.branch_code";

    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "50") int pageSize,
                                    @RequestParam(required = false) String search, @RequestParam(required = false) String status,
                                    Authentication auth) {
        Caller who = Caller.of(auth);
        ready();
        int size = Math.min(Math.max(pageSize, 1), 200), pg = Math.max(page, 1);
        StringBuilder where = new StringBuilder(" where 1=1");
        List<Object> args = new ArrayList<>();
        if (!who.admin()) { where.append(" and (n.branch_code = ? or n.branch_code is null)"); args.add(who.branch()); }
        if (status != null && Set.of("Draft", "Scheduled", "Sent").contains(status)) { where.append(" and n.status = ?"); args.add(status); }
        if (search != null && !search.isBlank()) {
            String s = search.trim();
            if (s.length() > 60) throw new ApiException(HttpStatus.BAD_REQUEST, "Search text is too long.");
            String like = "%" + s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            where.append(" and (n.title ilike ? or n.message ilike ?)");
            args.add(like);
            args.add(like);
        }
        args.add(size);
        args.add((long) (pg - 1) * size);
        List<Map<String, Object>> rows = db.queryForList("select n.*, b.branch_name, count(*) over() as _total from public.\"MB003003\" n "
                + "left join public.\"MB002002\" b on b.branch_code = n.branch_code" + where
                + " order by coalesce(n.sent_on, n.scheduled_for, n.created_on) desc, n.id desc limit ? offset ?", args.toArray());
        long total = 0;
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> r : rows) { total = ((Number) r.get("_total")).longValue(); items.add(record(r)); }
        return Map.of("items", items, "total", total, "page", pg, "pageSize", size);
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable long id, Authentication auth) {
        Caller who = Caller.of(auth);
        ready();
        return record(load(id, who));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody JsonNode body, Authentication auth, HttpServletRequest http) {
        Caller who = Caller.of(auth);
        ready();
        Map<String, Object> v = parse(body, who, true);
        String action = body.path("action").asText("send");
        boolean draft = "draft".equalsIgnoreCase(action);
        boolean scheduled = !draft && "scheduled".equalsIgnoreCase(body.path("schedule").asText("now"));
        Timestamp when = null;
        if (scheduled) {
            when = scheduledFor(body.path("dateTime").asText(""));
            if (when == null || when.getTime() <= System.currentTimeMillis()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Scheduled delivery must be a valid time in the future.", Map.of("dateTime", "Scheduled delivery must be in the future."));
            }
        }
        final Timestamp scheduledAt = when;
        long id = tx.execute(s -> {
            Long newId = db.queryForObject("insert into public.\"MB003003\" (title, message, branch_code, audience, status, scheduled_for, created_by) "
                            + "values (?,?,?,?,?,?,?) returning id", Long.class,
                    v.get("title"), v.get("message"), v.get("branch_code"), v.get("audience"),
                    draft ? "Draft" : scheduled ? "Scheduled" : "Draft", scheduledAt, who.id());
            audit.record(who, http, "CREATE", "push-notification", newId, "status=" + (draft ? "Draft" : scheduled ? "Scheduled" : "Sent") + " audience=" + v.get("audience"));
            return newId;
        });
        if (!draft && !scheduled) deliverOrFail(id, who, http);
        return ResponseEntity.status(HttpStatus.CREATED).body(record(load(id, who)));
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable long id, @RequestBody JsonNode body, Authentication auth, HttpServletRequest http) {
        Caller who = Caller.of(auth);
        ready();
        Map<String, Object> existing = load(id, who);
        if ("Sent".equals(existing.get("status"))) throw new ApiException(HttpStatus.CONFLICT, "A sent notification cannot be edited.");
        Map<String, Object> v = parse(body, who, false);
        Timestamp when = null;
        if ("Scheduled".equals(existing.get("status")) && "scheduled".equalsIgnoreCase(body.path("schedule").asText("")) && body.hasNonNull("dateTime")) {
            when = scheduledFor(body.path("dateTime").asText(""));
            if (when == null || when.getTime() <= System.currentTimeMillis()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Scheduled delivery must be a valid time in the future.", Map.of("dateTime", "Scheduled delivery must be in the future."));
            }
        }
        List<String> sets = new ArrayList<>(List.of("title = ?", "message = ?", "audience = ?", "branch_code = ?"));
        List<Object> args = new ArrayList<>(Arrays.asList(v.get("title"), v.get("message"), v.get("audience"), v.get("branch_code")));
        if (when != null) { sets.add("scheduled_for = ?"); args.add(when); }
        sets.add("modified_by = ?");
        args.add(who.id());
        sets.add("modified_on = now()");
        args.add(id);
        int n = db.update("update public.\"MB003003\" set " + String.join(", ", sets) + " where id = ? and status in ('Draft','Scheduled')", args.toArray());
        if (n == 0) throw new ApiException(HttpStatus.CONFLICT, "This notification was already sent.");
        audit.record(who, http, "UPDATE", "push-notification", id, "title=" + v.get("title"));
        return record(load(id, who));
    }

    @PostMapping("/{id}/send")
    public Map<String, Object> send(@PathVariable long id, Authentication auth, HttpServletRequest http) {
        Caller who = Caller.of(auth);
        ready();
        load(id, who);
        deliverOrFail(id, who, http);
        return record(load(id, who));
    }

    // ------------------------------------------------------------------ delivery

    private void deliverOrFail(long id, Caller who, HttpServletRequest http) {
        int count = deliver(id, who == null ? null : who.id());
        if (count < 0) throw new ApiException(HttpStatus.CONFLICT, "This notification was already sent.");
        audit.record(who, http, "SEND", "push-notification", id, "recipients=" + count);
    }

    /** Copies the message to each target customer's inbox. Returns the number of customers, or -1 if it was not pending. */
    private int deliver(long id, Long by) {
        Integer result = tx.execute(s -> {
            List<Map<String, Object>> rows = db.queryForList("select * from public.\"MB003003\" where id = ? and status in ('Draft','Scheduled') for update", id);
            if (rows.isEmpty()) return -1;
            Map<String, Object> n = rows.get(0);
            String branch = (String) n.get("branch_code");
            String audience = (String) n.get("audience");
            List<Object> args = new ArrayList<>();
            String text = n.get("title") + ": " + n.get("message");
            args.add(text.substring(0, Math.min(text.length(), 1000)));
            StringBuilder sql = new StringBuilder("insert into public.\"MB003001\" (branch_code, customer_no, notification) "
                    + "select c.branch_code, c.customer_no, ? from (select distinct on (a.customer_no) a.customer_no, a.branch_code "
                    + "from public.\"MB002003\" a");
            if (branch != null) { sql.append(" where a.branch_code = ?"); args.add(branch); }
            sql.append(" order by a.customer_no, a.created_on) c");
            if (PENDING_MPIN.equals(audience)) {
                sql.append(" where not exists (select 1 from public.\"MB001003\" m where m.customer_id = c.customer_no and m.status = 'ACTIVE')");
            } else if (ACTIVE_LOANS.equals(audience)) {
                sql.append(" where exists (select 1 from public.\"MB002003\" l where l.customer_no = c.customer_no "
                        + "and upper(l.account_type) like 'LOAN%' and upper(l.account_status) not like 'CLOS%')");
            }
            int count = db.update(sql.toString(), args.toArray());
            db.update("update public.\"MB003003\" set status = 'Sent', sent_on = now(), recipients = ?, sent_by = ?, modified_on = now() where id = ?",
                    count, by, id);
            return count;
        });
        return result == null ? -1 : result;
    }

    /** Delivers scheduled notifications that are due. Safe to run on several servers at once. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void deliverDue() {
        try {
            if (!Boolean.TRUE.equals(tableReady) && !tableExists()) return;
            List<Long> due = db.query("select id from public.\"MB003003\" where status = 'Scheduled' and scheduled_for <= now() order by scheduled_for limit 20",
                    (rs, i) -> rs.getLong(1));
            for (Long id : due) {
                int count = deliver(id, null);
                if (count >= 0) audit.record(null, null, "SEND", "push-notification", id, "scheduled delivery, recipients=" + count);
            }
        } catch (Exception e) {
            log.error("Scheduled notification delivery failed", e);
        }
    }

    // ------------------------------------------------------------------ validation

    private Map<String, Object> parse(JsonNode body, Caller who, boolean create) {
        if (body == null || !body.isObject()) throw new ApiException(HttpStatus.BAD_REQUEST, "Request body must be a JSON object.");
        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, Object> v = new LinkedHashMap<>();
        String title = body.path("title").asText("").trim();
        String message = body.path("message").asText("").trim();
        String audience = body.path("audience").asText("").trim();
        if (title.length() < 3 || title.length() > 80) errors.put("title", "Title must be 3 to 80 characters.");
        else if (bad(title)) errors.put("title", "Title contains characters that are not allowed.");
        if (message.length() < 5 || message.length() > 500) errors.put("message", "Message must be 5 to 500 characters.");
        else if (message.chars().anyMatch(c -> (c < 0x20 && c != '\n' && c != '\r' && c != '\t') || c == 0x7f)) errors.put("message", "Message contains characters that are not allowed.");
        if (!AUDIENCES.contains(audience)) errors.put("audience", "Select an audience.");
        String branch = null;
        String wanted = body.path("branch").asText("").trim();
        if (who.admin()) {
            branch = resolveBranch(wanted, errors);
        } else {
            String requested = resolveBranch(wanted, errors);
            if (errors.get("branch") == null && !who.branch().equalsIgnoreCase(Objects.toString(requested, ""))) {
                throw new ApiException(HttpStatus.FORBIDDEN, "You can only send notifications to your own branch.");
            }
            branch = who.branch();
        }
        if (!errors.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, String.join(" ", errors.values().stream().limit(3).toList()), errors);
        v.put("title", title);
        v.put("message", message);
        v.put("audience", audience);
        v.put("branch_code", branch);
        return v;
    }

    private static boolean bad(String s) {
        return s.chars().anyMatch(c -> c < 0x20 || c == 0x7f) || s.indexOf('<') >= 0 || s.indexOf('>') >= 0;
    }

    /** null = all branches. Accepts a branch code or branch name. */
    private String resolveBranch(String wanted, Map<String, String> errors) {
        if (wanted.isEmpty()) { errors.put("branch", "Select a branch."); return null; }
        if (wanted.equalsIgnoreCase("ALL") || wanted.equalsIgnoreCase("All branches")) return null;
        List<String> codes = db.query("select branch_code from public.\"MB002002\" where upper(branch_code) = upper(?) or upper(branch_name) = upper(?) "
                + "order by (upper(branch_code) = upper(?)) desc limit 1", (rs, i) -> rs.getString(1), wanted, wanted, wanted);
        if (codes.isEmpty()) { errors.put("branch", "Unknown branch."); return null; }
        return codes.get(0);
    }

    private Timestamp scheduledFor(String text) {
        try {
            return Timestamp.from(LocalDateTime.parse(text).atZone(IST).toInstant());
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> load(long id, Caller who) {
        List<Object> args = new ArrayList<>(List.of(id));
        String scope = "";
        if (!who.admin()) { scope = " and (n.branch_code = ? or n.branch_code is null)"; args.add(who.branch()); }
        List<Map<String, Object>> rows = db.queryForList(SELECT + " where n.id = ?" + scope, args.toArray());
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "Notification not found.");
        return rows.get(0);
    }

    private boolean tableExists() {
        Boolean ok = db.queryForObject("select to_regclass('public.\"MB003003\"') is not null", Boolean.class);
        if (Boolean.TRUE.equals(ok)) tableReady = true;
        return Boolean.TRUE.equals(ok);
    }

    private void ready() {
        if (Boolean.TRUE.equals(tableReady)) return;
        if (!tableExists()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Table MB003003 is missing. Run sql/03_mobile_utilities.sql on the database.");
    }

    private Map<String, Object> record(Map<String, Object> r) {
        String status = Objects.toString(r.get("status"), "Draft");
        Timestamp shown = "Sent".equals(status) ? (Timestamp) r.get("sent_on") : "Scheduled".equals(status) ? (Timestamp) r.get("scheduled_for") : null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.get("id").toString());
        m.put("title", r.get("title"));
        m.put("branch", r.get("branch_code") == null ? "All branches" : Objects.toString(r.get("branch_name"), r.get("branch_code").toString()));
        m.put("branchCode", r.get("branch_code") == null ? "ALL" : r.get("branch_code"));
        m.put("audience", r.get("audience"));
        m.put("sentOn", shown == null ? "—" : shown.toInstant().atZone(IST).format(SHOWN));
        m.put("status", status);
        m.put("message", r.get("message"));
        m.put("recipients", r.get("recipients") instanceof Number n ? n.intValue() : 0);
        return m;
    }
}
