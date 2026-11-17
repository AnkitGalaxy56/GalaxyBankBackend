package com.abcbank.portal.controller;

import com.abcbank.portal.requests.ApiException;
import com.abcbank.portal.requests.AuditLog;
import com.abcbank.portal.requests.Caller;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Device reset: unbinds the phone registered to a customer (MB003002.device_id) and keeps a history (MB003004).
 *  - The customer number AND the registered mobile number must both match, so numbers cannot be guessed.
 *  - Staff can only reset customers of their own branch.
 *  - Every reset is stored in the history table and in the audit log; at most 20 resets per user per minute.
 */
@RestController
@RequestMapping("/mobile-utilities/device-resets")
public class DeviceResetController {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final Set<String> REASONS = Set.of("Lost or stolen phone", "Changed phone", "App not working", "Other");
    private static final Pattern CUSTOMER = Pattern.compile("^[A-Za-z0-9\\-]{3,30}$");
    private static final Pattern MOBILE = Pattern.compile("^[6-9][0-9]{9}$");

    private final JdbcTemplate db;
    private final AuditLog audit;
    private final Map<Long, Deque<Long>> recent = new ConcurrentHashMap<>();
    private volatile Boolean tableReady;

    public DeviceResetController(JdbcTemplate db, AuditLog audit) { this.db = db; this.audit = audit; }

    @GetMapping
    public Map<String, Object> history(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize, Authentication auth) {
        Caller who = Caller.of(auth);
        ready();
        int size = Math.min(Math.max(pageSize, 1), 100), pg = Math.max(page, 1);
        List<Object> args = new ArrayList<>();
        String where = "";
        if (!who.admin()) { where = " where h.branch_code = ?"; args.add(who.branch()); }
        args.add(size);
        args.add((long) (pg - 1) * size);
        List<Map<String, Object>> rows = db.queryForList("select h.*, b.branch_name, coalesce(nullif(u.full_name, ''), u.user_name) as by_name, count(*) over() as _total "
                + "from public.\"MB003004\" h left join public.\"MB002002\" b on b.branch_code = h.branch_code "
                + "left join public.\"MB001001\" u on u.user_id = h.created_by" + where + " order by h.created_on desc, h.id desc limit ? offset ?", args.toArray());
        long total = 0;
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            total = ((Number) r.get("_total")).longValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("customer", r.get("customer_no"));
            m.put("branch", Objects.toString(r.get("branch_name"), Objects.toString(r.get("branch_code"), "—")));
            m.put("reason", r.get("reason"));
            m.put("by", Objects.toString(r.get("by_name"), "—"));
            m.put("date", ((Timestamp) r.get("created_on")).toInstant().atZone(IST).format(DAY));
            m.put("status", r.get("status"));
            items.add(m);
        }
        return Map.of("items", items, "total", total, "page", pg, "pageSize", size);
    }

    @PostMapping("/lookup")
    public Map<String, Object> lookup(@RequestBody JsonNode body, Authentication auth) {
        Caller who = Caller.of(auth);
        ready();
        Map<String, Object> row = find(body, who, false);
        return summary(row);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> reset(@RequestBody JsonNode body, Authentication auth, HttpServletRequest http) {
        Caller who = Caller.of(auth);
        ready();
        throttle(who);
        String reason = body.path("reason").asText("").trim();
        if (!REASONS.contains(reason)) throw new ApiException(HttpStatus.BAD_REQUEST, "Select a reason for the reset.", Map.of("reason", "Select a reason."));
        Map<String, Object> row = find(body, who, true);
        if (row.get("device_id") == null) throw new ApiException(HttpStatus.CONFLICT, "No device is registered for this customer, so there is nothing to reset.");
        long id = ((Number) row.get("id")).longValue();
        db.update("update public.\"MB003002\" set device_id = null, modified_by = ?, modified_on = now() where id = ?", who.id(), id);
        db.update("insert into public.\"MB003004\" (customer_no, branch_code, mobile_masked, reason, status, created_by) values (?,?,?,?, 'completed', ?)",
                row.get("customer_no"), row.get("branch_code"), mask(row.get("mobile").toString()), reason, who.id());
        audit.record(who, http, "DEVICE_RESET", "device-reset", id, "customer=" + row.get("customer_no") + " reason=" + reason);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("customer", row.get("customer_no"));
        out.put("branch", Objects.toString(row.get("branch_name"), Objects.toString(row.get("branch_code"), "—")));
        out.put("reason", reason);
        out.put("by", who.name());
        out.put("date", java.time.ZonedDateTime.now(IST).format(DAY));
        out.put("status", "completed");
        return ResponseEntity.status(HttpStatus.CREATED).body(out);
    }

    // ------------------------------------------------------------------ helpers

    /** Finds the active registration only if customer number and mobile both match. The message is the same for every miss. */
    private Map<String, Object> find(JsonNode body, Caller who, boolean lock) {
        String customer = body.path("customerNumber").asText("").trim();
        String mobile = body.path("mobile").asText("").trim();
        Map<String, String> errors = new LinkedHashMap<>();
        if (!CUSTOMER.matcher(customer).matches()) errors.put("customerNumber", "Enter a valid customer number.");
        if (!MOBILE.matcher(mobile).matches()) errors.put("mobile", "Enter a valid 10-digit mobile number.");
        if (!errors.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, String.join(" ", errors.values()), errors);
        List<Object> args = new ArrayList<>(List.of(customer, mobile));
        String scope = "";
        if (!who.admin()) { scope = " and x.branch_code = ?"; args.add(who.branch()); }
        List<Map<String, Object>> rows = db.queryForList("select * from (select d.id, d.customer_no, d.mobile, d.device_id, d.created_on, "
                + "(select a.branch_code from public.\"MB002003\" a where a.customer_no = d.customer_no order by a.created_on limit 1) as branch_code "
                + "from public.\"MB003002\" d where d.customer_no = ? and d.mobile = ? and d.is_active order by d.created_on desc limit 1"
                + (lock ? " for update of d" : "") + ") x where 1=1" + scope, args.toArray());
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "No registered customer matches these details.");
        Map<String, Object> r = new LinkedHashMap<>(rows.get(0));
        Object code = r.get("branch_code");
        if (code != null) {
            List<String> name = db.query("select branch_name from public.\"MB002002\" where branch_code = ?", (rs, i) -> rs.getString(1), code);
            if (!name.isEmpty()) r.put("branch_name", name.get(0));
        }
        return r;
    }

    private Map<String, Object> summary(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("customerNumber", r.get("customer_no"));
        m.put("branch", Objects.toString(r.get("branch_name"), Objects.toString(r.get("branch_code"), "—")));
        m.put("mobile", mask(r.get("mobile").toString()));
        m.put("device", r.get("device_id") == null ? "No device registered" : r.get("device_id").toString());
        m.put("registeredOn", ((Timestamp) r.get("created_on")).toInstant().atZone(IST).format(DATE));
        return m;
    }

    private static String mask(String mobile) {
        return mobile.length() < 4 ? mobile : "XXXXXX" + mobile.substring(mobile.length() - 4);
    }

    private void throttle(Caller who) {
        long now = System.currentTimeMillis();
        Deque<Long> q = recent.computeIfAbsent(who.id(), k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > 60_000) q.pollFirst();
            if (q.size() >= 20) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many resets in a short time. Please wait a minute.");
            q.addLast(now);
        }
    }

    private void ready() {
        if (Boolean.TRUE.equals(tableReady)) return;
        Boolean ok = db.queryForObject("select to_regclass('public.\"MB003004\"') is not null", Boolean.class);
        if (!Boolean.TRUE.equals(ok)) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Table MB003004 is missing. Run sql/03_mobile_utilities.sql on the database.");
        tableReady = true;
    }
}
