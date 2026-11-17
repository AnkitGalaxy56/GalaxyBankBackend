package com.abcbank.portal.controller;

import com.abcbank.portal.requests.ApiException;
import com.abcbank.portal.requests.RequestModules;
import com.abcbank.portal.requests.RequestModules.Field;
import com.abcbank.portal.requests.RequestModules.Module;
import com.abcbank.portal.requests.RequestModules.Resolve;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Request-handling API (cheque book, stop payment, RTGS/NEFT/IMPS, FD/RD, EMI, standing
 * instruction, locker, loan). Every module is stored in its own MB004xxx table.
 *
 * Security model
 *  - JWT required (SecurityConfig). Only portal roles ADMIN / STAFF are accepted here.
 *  - Staff are limited to their own branch; the branch always comes from the signed token
 *    and from the account master, never from the request body.
 *  - Only fields listed in RequestModules can be written; every value is validated and bound as a
 *    query parameter (no string-built SQL values, no client-chosen column names).
 *  - Maker-checker: the creator (or last editor) of a request can never authorise it.
 *  - Editing an authorised request sends it back for authorisation.
 *  - Requests are never deleted; "delete" cancels a pending request.
 *  - Every create / edit / decision / cancel is written to MB005001.
 *  - Account numbers are masked in list responses; full values only in the single-record view.
 *  - Write calls are rate limited per user.
 */
@RestController
@RequestMapping("/request-handling")
public class RequestHandlingController {

    private static final Logger log = LoggerFactory.getLogger(RequestHandlingController.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter SUBMITTED = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final Set<String> PORTAL_ROLES = Set.of("ADMIN", "SUPER_ADMIN", "STAFF", "USER");
    private static final List<String> NOT_OPERATIVE = List.of("CLOS", "FROZ", "BLOCK", "DORMANT", "INOP", "SUSP");
    private static final String NAME_JOIN =
            "left join (select distinct on (customer_no) customer_no, full_name, mobile from public.\"MB001001\" "
                    + "where customer_no is not null order by customer_no) u on u.customer_no = %1$s.customer_no "
                    + "left join public.\"MB002002\" b on b.branch_code = %1$s.branch_code";

    private final JdbcTemplate db;
    private final Map<Long, Deque<Long>> writes = new ConcurrentHashMap<>();
    private volatile Map<String, Set<String>> columnCache;
    private volatile boolean auditReady;

    @Value("${portal.requests.max-amount:100000000}")
    private BigDecimal maxAmount;
    @Value("${portal.requests.staff-can-authorise:true}")
    private boolean staffCanAuthorise;
    @Value("${portal.requests.writes-per-minute:60}")
    private int writesPerMinute;

    public RequestHandlingController(JdbcTemplate db) { this.db = db; }

    @PostConstruct
    void init() {
        try {
            db.execute("create table if not exists public.\"MB005001\" ("
                    + "id bigserial primary key, at timestamptz not null default now(), actor_id bigint, actor_name varchar(80), "
                    + "actor_role varchar(30), ip varchar(64), action varchar(30) not null, module varchar(50), record_id bigint, "
                    + "old_status varchar(20), new_status varchar(20), detail varchar(500))");
            auditReady = true;
        } catch (Exception e) {
            log.error("MB005001 (audit log) could not be created; request actions will NOT be audited. Run sql/02_request_handling_additions.sql", e);
        }
    }

    // ------------------------------------------------------------------ list / get

    @GetMapping("/{module}")
    public Map<String, Object> list(@PathVariable String module,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "200") int pageSize,
                                    @RequestParam(required = false) String search,
                                    @RequestParam(required = false) String status,
                                    Authentication auth) {
        Who who = who(auth);
        Module m = module(module);
        Set<String> cols = columns(m);
        int size = Math.min(Math.max(pageSize, 1), 500);
        int pg = Math.max(page, 0);

        StringBuilder where = new StringBuilder(" where 1=1");
        List<Object> args = new ArrayList<>();
        if (!who.admin()) { where.append(" and t.branch_code = ?"); args.add(who.branch()); }

        if (status != null && !status.isBlank()) {
            List<String> db = dbStatuses(status.trim());
            if (db.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown status filter.");
            where.append(" and t.req_status in (").append(String.join(",", Collections.nCopies(db.size(), "?"))).append(")");
            args.addAll(db);
        }
        if (search != null && !search.isBlank()) {
            String s = search.trim();
            if (s.length() > 60) throw new ApiException(HttpStatus.BAD_REQUEST, "Search text is too long.");
            String like = "%" + s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            List<String> parts = new ArrayList<>();
            parts.add("t.id::text ilike ?");
            parts.add("t.customer_no ilike ?");
            parts.add("u.full_name ilike ?");
            for (Field f : active(m, cols)) {
                if (f.type == RequestModules.Type.STR && !f.mask) parts.add("t.\"" + f.column + "\" ilike ?");
            }
            where.append(" and (").append(String.join(" or ", parts)).append(")");
            for (int i = 0; i < parts.size(); i++) args.add(like);
        }

        String sql = "select t.*, u.full_name as _customer_name, u.mobile as _customer_mobile, b.branch_name as _branch_name, count(*) over() as _total from "
                + m.qualified() + " t " + String.format(NAME_JOIN, "t") + where
                + " order by t.created_on desc, t.id desc limit ? offset ?";
        args.add(size);
        args.add((long) pg * size);
        List<Map<String, Object>> rows = db.queryForList(sql, args.toArray());

        List<Map<String, Object>> items = new ArrayList<>();
        long total = 0;
        for (Map<String, Object> r : rows) {
            total = ((Number) r.get("_total")).longValue();
            items.add(record(m, cols, r, who, true));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        out.put("total", total);
        out.put("page", pg);
        out.put("pageSize", size);
        return out;
    }

    @GetMapping("/{module}/{id}")
    public Map<String, Object> get(@PathVariable String module, @PathVariable long id, Authentication auth) {
        Who who = who(auth);
        Module m = module(module);
        Set<String> cols = columns(m);
        List<Object> args = new ArrayList<>(List.of(id));
        String scope = "";
        if (!who.admin()) { scope = " and t.branch_code = ?"; args.add(who.branch()); }
        List<Map<String, Object>> rows = db.queryForList(
                "select t.*, u.full_name as _customer_name, u.mobile as _customer_mobile, b.branch_name as _branch_name from " + m.qualified() + " t "
                        + String.format(NAME_JOIN, "t") + " where t.id = ?" + scope, args.toArray());
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "Request not found.");
        return record(m, cols, rows.get(0), who, false);
    }

    // ------------------------------------------------------------------ create

    @PostMapping("/{module}")
    @Transactional
    public ResponseEntity<Map<String, Object>> create(@PathVariable String module, @RequestBody JsonNode body,
                                                      Authentication auth, HttpServletRequest http) {
        Who who = who(auth);
        throttle(who);
        Module m = module(module);
        Set<String> cols = columns(m);
        requireObject(body);

        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, Object> v = parseFields(m, cols, body, true, errors);
        if (errors.isEmpty()) resolveIdentity(m, cols, who, v, errors);
        // Requests submitted from this portal are Control Panel-originated.
        // Older installations use SMALLINT (1 = Mobile App, 2 = Control Panel),
        // while some databases define is_sys as BOOLEAN (false = Mobile App, true = Control Panel).
        // Detect the actual database type so the INSERT parameter always matches it.
        if (errors.isEmpty() && "PAY".equals(m.prefix()) && cols.contains("is_sys")) {
            v.put("is_sys", isBooleanColumn(m, "is_sys") ? Boolean.TRUE : 2);
        }
        if (errors.isEmpty()) applyRules(m, cols, true, v, Map.of(), errors);
        if (!errors.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Please correct the highlighted fields.", errors);

        v.put("req_status", "PENDING");
        v.put("created_by", who.id());
        List<String> names = new ArrayList<>(v.keySet());
        String sql = "with ins as (insert into " + m.qualified() + " (" + quoted(names) + ") values ("
                + String.join(",", Collections.nCopies(names.size(), "?")) + ") returning *) "
                + "select ins.*, u.full_name as _customer_name, u.mobile as _customer_mobile, b.branch_name as _branch_name from ins "
                + String.format(NAME_JOIN, "ins");
        List<Map<String, Object>> rows = db.queryForList(sql, names.stream().map(v::get).toArray());
        Map<String, Object> row = rows.get(0);
        audit(who, http, "CREATE", m, ((Number) row.get("id")).longValue(), null, "PENDING", null);
        return ResponseEntity.status(HttpStatus.CREATED).body(record(m, cols, row, who, false));
    }

    // ------------------------------------------------------------------ update

    @PutMapping("/{module}/{id}")
    @Transactional
    public Map<String, Object> update(@PathVariable String module, @PathVariable long id, @RequestBody JsonNode body,
                                      Authentication auth, HttpServletRequest http) {
        Who who = who(auth);
        throttle(who);
        Module m = module(module);
        Set<String> cols = columns(m);
        requireObject(body);

        Map<String, Object> existing = loadRow(m, id, who);
        String status = str(existing.get("req_status"));
        boolean mine = Objects.equals(num(existing.get("created_by")), who.id());
        if (!("APPROVED".equals(status) || ("PENDING".equals(status) && mine))) {
            throw new ApiException(HttpStatus.CONFLICT, "Only authorised requests, or your own pending requests, can be edited.");
        }

        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, Object> v = parseFields(m, cols, body, false, errors);
        if (errors.isEmpty()) applyRules(m, cols, false, v, existing, errors);
        if (!errors.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Please correct the highlighted fields.", errors);
        if (v.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "No editable fields were supplied.");

        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        for (var e : v.entrySet()) { sets.add("\"" + e.getKey() + "\" = ?"); args.add(e.getValue()); }
        sets.add("\"req_status\" = 'PENDING'");
        sets.add("\"verified_by\" = null");
        sets.add("\"verified_on\" = null");
        sets.add("\"modified_by\" = ?");
        args.add(who.id());
        sets.add("\"modified_on\" = now()");
        args.add(id);
        args.add(status);
        String scope = "";
        if (!who.admin()) { scope = " and t.branch_code = ?"; args.add(who.branch()); }

        String sql = "with upd as (update " + m.qualified() + " t set " + String.join(", ", sets)
                + " where t.id = ? and t.req_status = ?" + scope + " returning t.*) "
                + "select upd.*, u.full_name as _customer_name, u.mobile as _customer_mobile, b.branch_name as _branch_name from upd "
                + String.format(NAME_JOIN, "upd");
        List<Map<String, Object>> rows = db.queryForList(sql, args.toArray());
        if (rows.isEmpty()) throw new ApiException(HttpStatus.CONFLICT, "This request was changed by someone else. Reload and try again.");
        audit(who, http, "UPDATE", m, id, status, "PENDING", "fields: " + String.join(",", v.keySet()));
        return record(m, cols, rows.get(0), who, false);
    }

    // ------------------------------------------------------------------ authorise / reject

    @PatchMapping("/{module}/{id}/decision")
    @Transactional
    public Map<String, Object> decide(@PathVariable String module, @PathVariable long id, @RequestBody JsonNode body,
                                      Authentication auth, HttpServletRequest http) {
        Who who = who(auth);
        throttle(who);
        if (!who.admin() && !staffCanAuthorise) throw new ApiException(HttpStatus.FORBIDDEN, "You are not allowed to authorise requests.");
        Module m = module(module);
        Set<String> cols = columns(m);
        requireObject(body);

        String wanted = body.path("status").asText("").trim().toUpperCase(Locale.ROOT);
        String decision = Set.of("AUTHORISED", "AUTHORIZED", "APPROVED").contains(wanted) ? "APPROVED"
                : "REJECTED".equals(wanted) ? "REJECTED" : null;
        if (decision == null) throw new ApiException(HttpStatus.BAD_REQUEST, "Status must be Authorised or Rejected.");
        String remarks = body.path("remarks").asText("").trim();
        if (remarks.length() > 255) throw new ApiException(HttpStatus.BAD_REQUEST, "Remarks are too long.");

        Map<String, Object> existing = loadRow(m, id, who);
        if (!"PENDING".equals(str(existing.get("req_status")))) {
            throw new ApiException(HttpStatus.CONFLICT, "This request is no longer pending.");
        }
        Long creator = num(existing.get("created_by"));
        Long editor = num(existing.get("modified_by"));
        if (Objects.equals(creator, who.id()) || Objects.equals(editor, who.id())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Maker-checker rule: you cannot authorise a request you created or edited.");
        }

        List<Object> args = new ArrayList<>(List.of(decision, who.id(), who.id(), id));
        String scope = "";
        if (!who.admin()) { scope = " and t.branch_code = ?"; args.add(who.branch()); }
        String sql = "with upd as (update " + m.qualified() + " t set req_status = ?, verified_by = ?, verified_on = now(), "
                + "modified_by = ?, modified_on = now() where t.id = ? and t.req_status = 'PENDING'" + scope + " returning t.*) "
                + "select upd.*, u.full_name as _customer_name, u.mobile as _customer_mobile, b.branch_name as _branch_name from upd "
                + String.format(NAME_JOIN, "upd");
        List<Map<String, Object>> rows = db.queryForList(sql, args.toArray());
        if (rows.isEmpty()) throw new ApiException(HttpStatus.CONFLICT, "This request was already processed by someone else.");
        audit(who, http, "APPROVED".equals(decision) ? "AUTHORISE" : "REJECT", m, id, "PENDING", decision, remarks.isEmpty() ? null : remarks);
        return record(m, cols, rows.get(0), who, false);
    }

    // ------------------------------------------------------------------ cancel ("delete")

    @DeleteMapping("/{module}/{id}")
    @Transactional
    public Map<String, Object> cancel(@PathVariable String module, @PathVariable long id,
                                      Authentication auth, HttpServletRequest http) {
        Who who = who(auth);
        throttle(who);
        Module m = module(module);
        Set<String> cols = columns(m);
        Map<String, Object> existing = loadRow(m, id, who);
        if (!"PENDING".equals(str(existing.get("req_status")))) {
            throw new ApiException(HttpStatus.CONFLICT, "Only pending requests can be cancelled.");
        }
        if (!Objects.equals(num(existing.get("created_by")), who.id()) && !who.admin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the creator or an administrator can cancel this request.");
        }
        List<Object> args = new ArrayList<>(List.of(who.id(), id));
        String scope = "";
        if (!who.admin()) { scope = " and t.branch_code = ?"; args.add(who.branch()); }
        String sql = "with upd as (update " + m.qualified() + " t set req_status = 'CANCELLED', modified_by = ?, modified_on = now() "
                + "where t.id = ? and t.req_status = 'PENDING'" + scope + " returning t.*) "
                + "select upd.*, u.full_name as _customer_name, u.mobile as _customer_mobile, b.branch_name as _branch_name from upd "
                + String.format(NAME_JOIN, "upd");
        List<Map<String, Object>> rows = db.queryForList(sql, args.toArray());
        if (rows.isEmpty()) throw new ApiException(HttpStatus.CONFLICT, "This request was already processed.");
        audit(who, http, "CANCEL", m, id, "PENDING", "CANCELLED", null);
        return record(m, cols, rows.get(0), who, false);
    }

    // ------------------------------------------------------------------ caller

    private record Who(long id, String name, String role, String branch) {
        boolean admin() { return role.equals("ADMIN") || role.equals("SUPER_ADMIN"); }
    }

    private Who who(Authentication auth) {
        if (auth == null || auth.getName() == null || !(auth.getDetails() instanceof Map<?, ?> claims)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required.");
        }
        long id;
        try { id = Long.parseLong(auth.getName()); }
        catch (NumberFormatException e) { throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid session."); }
        String role = Objects.toString(claims.get("role"), "").toUpperCase(Locale.ROOT);
        if (!PORTAL_ROLES.contains(role)) throw new ApiException(HttpStatus.FORBIDDEN, "Your role cannot access request handling.");
        String branch = Objects.toString(claims.get("branch"), "").trim();
        Who w = new Who(id, Objects.toString(claims.get("username"), ""), role, branch);
        if (!w.admin() && branch.isEmpty()) throw new ApiException(HttpStatus.FORBIDDEN, "Your user has no branch assigned.");
        return w;
    }

    private void throttle(Who who) {
        long now = System.currentTimeMillis();
        Deque<Long> q = writes.computeIfAbsent(who.id(), k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > 60_000) q.pollFirst();
            if (q.size() >= writesPerMinute) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many changes in a short time. Please wait a minute and try again.");
            q.addLast(now);
        }
    }

    // ------------------------------------------------------------------ metadata

    private Module module(String key) {
        Module m = RequestModules.find(key);
        if (m == null) throw new ApiException(HttpStatus.NOT_FOUND, "Unknown request module.");
        return m;
    }

    private Set<String> columns(Module m) {
        Map<String, Set<String>> c = columnCache;
        if (c == null) {
            List<String> tables = RequestModules.all().stream().map(Module::table).distinct().toList();
            Map<String, Set<String>> tmp = new HashMap<>();
            db.query("select table_name, column_name from information_schema.columns where table_schema = 'public' and table_name in ("
                            + String.join(",", Collections.nCopies(tables.size(), "?")) + ")",
                    (RowCallbackHandler) rs -> tmp.computeIfAbsent(rs.getString(1), k -> new HashSet<>()).add(rs.getString(2)),
                    tables.toArray());
            if (!tmp.isEmpty()) columnCache = tmp;
            c = tmp;
        }
        Set<String> s = c.get(m.table());
        if (s == null || s.isEmpty()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "This module's database table is not available.");
        return s;
    }

    private boolean isBooleanColumn(Module m, String column) {
        String dataType = db.queryForObject(
                "select data_type from information_schema.columns " +
                "where table_schema = 'public' and table_name = ? and column_name = ?",
                String.class, m.table(), column);
        return "boolean".equalsIgnoreCase(dataType);
    }

    private List<Field> active(Module m, Set<String> cols) {
        return m.fields().stream().filter(f -> cols.contains(f.column)).toList();
    }

    private Map<String, Object> loadRow(Module m, long id, Who who) {
        List<Object> args = new ArrayList<>(List.of(id));
        String scope = "";
        if (!who.admin()) { scope = " and branch_code = ?"; args.add(who.branch()); }
        List<Map<String, Object>> rows = db.queryForList("select * from " + m.qualified() + " where id = ?" + scope, args.toArray());
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "Request not found.");
        return rows.get(0);
    }

    // ------------------------------------------------------------------ parsing and validation

    private void requireObject(JsonNode body) {
        if (body == null || !body.isObject()) throw new ApiException(HttpStatus.BAD_REQUEST, "Request body must be a JSON object.");
        if (body.size() > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "Too many fields.");
    }

    private Map<String, Object> parseFields(Module m, Set<String> cols, JsonNode body, boolean create, Map<String, String> errors) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Field f : active(m, cols)) {
            if (f.readOnly) continue;
            if (!create && f.identity) continue;
            String raw = raw(body, f, errors);
            if (raw == null) {
                if (!create) continue;
                if (f.defaultValue != null) raw = f.defaultValue;
                else {
                    if (f.required) fieldError(errors, f, label(f) + " is required.");
                    continue;
                }
            }
            Object value = parse(f, raw, errors);
            if (value != null) out.put(f.column, value);
        }
        return out;
    }

    private String raw(JsonNode body, Field f, Map<String, String> errors) {
        List<String> keys = new ArrayList<>(f.aliases);
        keys.add(f.column);
        for (String k : keys) {
            JsonNode n = body.get(k);
            if (n == null || n.isNull()) continue;
            if (n.isContainerNode()) { fieldError(errors, f, label(f) + " has an invalid value."); return null; }
            String s = n.asText().trim();
            if (!s.isEmpty()) return s;
        }
        return null;
    }

    private Object parse(Field f, String raw, Map<String, String> errors) {
        String l = label(f);
        try {
            switch (f.type) {
                case STR: {
                    String s = f.upper ? raw.toUpperCase(Locale.ROOT) : raw;
                    if (s.chars().anyMatch(c -> c < 0x20 || c == 0x7f) || s.indexOf('<') >= 0 || s.indexOf('>') >= 0) {
                        fieldError(errors, f, l + " contains characters that are not allowed."); return null;
                    }
                    if (s.length() > f.max) { fieldError(errors, f, l + " must be at most " + f.max + " characters."); return null; }
                    if (f.allowed != null) {
                        for (String a : f.allowed) if (a.equalsIgnoreCase(s)) return a;
                        fieldError(errors, f, l + " must be one of: " + String.join(", ", f.allowed) + "."); return null;
                    }
                    if (f.pattern != null && !f.pattern.matcher(s).matches()) {
                        fieldError(errors, f, f.message != null ? f.message : l + " is not valid."); return null;
                    }
                    return s;
                }
                case DEC: {
                    BigDecimal d = new BigDecimal(raw.replace(",", "").replace("₹", "").replace("Rs.", "").replace(" ", ""));
                    if (d.stripTrailingZeros().scale() > f.scale) { fieldError(errors, f, l + " can have at most " + f.scale + " decimal places."); return null; }
                    d = d.setScale(f.scale, java.math.RoundingMode.UNNECESSARY);
                    if (f.min != null && d.compareTo(f.min) < 0) { fieldError(errors, f, l + " must be at least " + f.min.toPlainString() + "."); return null; }
                    if (f.scale == 4 && d.compareTo(new BigDecimal("100")) > 0) { fieldError(errors, f, l + " cannot exceed 100."); return null; }
                    if (f.scale == 2 && d.compareTo(maxAmount) > 0) { fieldError(errors, f, l + " exceeds the allowed limit."); return null; }
                    return d;
                }
                case INT: {
                    int n = Integer.parseInt(raw.replace(",", ""));
                    if (f.min != null && n < f.min.intValue()) { fieldError(errors, f, l + " must be at least " + f.min.toPlainString() + "."); return null; }
                    if (n > 10_000) { fieldError(errors, f, l + " is too large."); return null; }
                    return n;
                }
                case DATE: {
                    LocalDate d = LocalDate.parse(raw);
                    if (d.getYear() < 1900 || d.getYear() > 2200) { fieldError(errors, f, l + " is out of range."); return null; }
                    return d;
                }
                case BOOL: {
                    String s = raw.toLowerCase(Locale.ROOT);
                    if (Set.of("true", "yes", "y", "1").contains(s)) return Boolean.TRUE;
                    if (Set.of("false", "no", "n", "0").contains(s)) return Boolean.FALSE;
                    fieldError(errors, f, l + " must be Yes or No."); return null;
                }
                default: return null;
            }
        } catch (NumberFormatException | java.time.format.DateTimeParseException | ArithmeticException e) {
            fieldError(errors, f, l + (f.type == RequestModules.Type.DATE ? " must be a date (YYYY-MM-DD)." : " must be a valid number."));
            return null;
        }
    }

    private void fieldError(Map<String, String> errors, Field f, String message) {
        for (String a : f.aliases) errors.putIfAbsent(a, message);
    }

    private static String label(Field f) {
        if (f.label != null) return f.label;
        String a = f.aliases.get(0).replaceAll("([a-z])([A-Z])", "$1 $2");
        return Character.toUpperCase(a.charAt(0)) + a.substring(1);
    }

    // ------------------------------------------------------------------ identity (account / customer / branch)

    private void resolveIdentity(Module m, Set<String> cols, Who who, Map<String, Object> v, Map<String, String> errors) {
        Field idField = m.fields().stream().filter(f -> f.identity).findFirst().orElseThrow();
        Object key = v.get(m.identityColumn());
        if (key == null) return;
        Map<String, Object> acct;
        if (m.resolve() == Resolve.ACCOUNT) {
            acct = firstAccount("account_no = ?", key, null);
            if (acct == null) { fieldError(errors, idField, "Account not found."); return; }
            requireBranch(who, acct);
            requireOperative(acct, idField, errors);
        } else {
            acct = firstAccount("customer_no = ?", key, who.admin() ? null : who.branch());
            if (acct == null && m.resolve() == Resolve.CUSTOMER_ONLY) {
                Integer known = db.queryForObject("select count(*) from public.\"MB001001\" where customer_no = ?", Integer.class, key);
                if (known != null && known > 0 && !who.admin()) { v.put("branch_code", who.branch()); return; }
                fieldError(errors, idField, known != null && known > 0
                        ? "This customer has no account, so the branch cannot be determined." : "Customer not found.");
                return;
            }
            if (acct == null) { fieldError(errors, idField, who.admin() ? "No account found for this customer." : "No account found for this customer in your branch."); return; }
            if (m.resolve() == Resolve.CUSTOMER) v.put("account_no", acct.get("account_no"));
        }
        if (!errors.isEmpty()) return;
        v.put("branch_code", acct.get("branch_code"));
        if (m.resolve() == Resolve.ACCOUNT || m.resolve() == Resolve.CUSTOMER) v.put("customer_no", acct.get("customer_no"));
        if (m.statusCol() != null && cols.contains(m.statusCol())) v.put(m.statusCol(), acct.get("account_status"));
        if (m.typeCol() != null && cols.contains(m.typeCol())) v.put(m.typeCol(), acct.get("account_type"));
        // Secondary accounts (loan account, FD account) must belong to the same customer.
        for (Field f : active(m, cols)) {
            if (!f.owned || v.get(f.column) == null) continue;
            Integer n = db.queryForObject("select count(*) from public.\"MB002003\" where account_no = ? and customer_no = ?",
                    Integer.class, v.get(f.column), v.get("customer_no"));
            if (n == null || n == 0) fieldError(errors, f, "This account was not found for the customer.");
        }
    }

    private Map<String, Object> firstAccount(String condition, Object key, String branch) {
        List<Object> args = new ArrayList<>(List.of(key));
        String sql = "select account_no, customer_no, branch_code, account_type, account_status from public.\"MB002003\" where " + condition;
        if (branch != null) { sql += " and branch_code = ?"; args.add(branch); }
        sql += " order by created_on asc limit 1";
        List<Map<String, Object>> rows = db.queryForList(sql, args.toArray());
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void requireBranch(Who who, Map<String, Object> acct) {
        if (!who.admin() && !who.branch().equals(str(acct.get("branch_code")))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This account does not belong to your branch.");
        }
    }

    private void requireOperative(Map<String, Object> acct, Field idField, Map<String, String> errors) {
        String s = Objects.toString(acct.get("account_status"), "").toUpperCase(Locale.ROOT);
        for (String bad : NOT_OPERATIVE) {
            if (s.contains(bad)) { fieldError(errors, idField, "This account is not operative (" + acct.get("account_status") + ")."); return; }
        }
    }

    // ------------------------------------------------------------------ module rules (server-side calculations)

    private void applyRules(Module m, Set<String> cols, boolean create, Map<String, Object> v,
                            Map<String, Object> existing, Map<String, String> errors) {
        switch (m.prefix()) {
            case "PAY": {
                // The remitting branch's IFSC comes from the branch master, not from the browser.
                if (create && cols.contains("ifsc_code") && v.get("branch_code") != null) {
                    List<String> ifsc = db.query("select ifsc_code::text from public.\"MB002002\" where branch_code = ?",
                            (rs, n) -> rs.getString(1), v.get("branch_code"));
                    if (!ifsc.isEmpty() && ifsc.get(0) != null) v.put("ifsc_code", ifsc.get(0));
                }
                break;
            }
            case "SI": {
                Object debit = create ? v.get("account_no") : existing.get("account_no");
                Object credit = v.containsKey("cr_acid") ? v.get("cr_acid") : existing.get("cr_acid");
                if (debit != null && debit.equals(credit)) {
                    errors.put("creditAccount", "Debit and credit accounts must be different.");
                }
                break;
            }
            case "FD":
            case "RD": {
                if (create) {
                    v.putIfAbsent("asof_date", LocalDate.now(IST));
                    v.putIfAbsent("cert_date", v.get("asof_date"));
                }
                LocalDate asof = (LocalDate) (v.containsKey("asof_date") ? v.get("asof_date") : toDate(existing.get("asof_date")));
                Object tenure = v.containsKey("tenure") ? v.get("tenure") : existing.get("tenure");
                if (asof != null && tenure instanceof Number n && cols.contains("maturity_date")) {
                    v.put("maturity_date", asof.plusMonths(n.longValue()));
                }
                break;
            }
            case "LN": {
                BigDecimal income = dec(v.containsKey("total_income") ? v.get("total_income") : existing.get("total_income"));
                BigDecimal exp = dec(v.containsKey("total_exp") ? v.get("total_exp") : existing.get("total_exp"));
                if (cols.contains("eligibility") && (income != null || exp != null)) {
                    BigDecimal net = (income == null ? BigDecimal.ZERO : income).subtract(exp == null ? BigDecimal.ZERO : exp);
                    // Same rule the web form shows: (income - expense) x 12 months x 3 years, never negative.
                    v.put("eligibility", net.signum() <= 0 ? "0" : net.multiply(BigDecimal.valueOf(36)).setScale(0, java.math.RoundingMode.DOWN).toPlainString());
                }
                break;
            }
            default:
        }
    }

    // ------------------------------------------------------------------ response shape

    private Map<String, Object> record(Module m, Set<String> cols, Map<String, Object> row, Who who, boolean mask) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (Field f : m.fields()) {
            if (!cols.contains(f.column)) continue;
            Object raw = row.get(f.column);
            if (raw == null) continue;
            String s = fmt(raw);
            if ("is_sys".equals(f.column)) {
                boolean controlPanel = raw instanceof Boolean
                        ? Boolean.TRUE.equals(raw)
                        : "2".equals(s);
                s = controlPanel ? "Control Panel" : "Mobile App";
            }
            if (mask && f.mask) s = maskValue(s);
            for (String a : f.aliases) fields.put(a, s);
        }
        String customer = str(row.get("_customer_name"));
        if (customer != null) for (String a : m.nameAliases()) fields.putIfAbsent(a, customer);
        // Payment requests display the account holder's registered mobile from MB001001.
        // The payment table stores the customer number, not a separate mobile column.
        if ("rtgs-neft-imps".equals(m.key())) {
            String customerMobile = str(row.get("_customer_mobile"));
            if (customerMobile != null) fields.put("mobile", customerMobile);
        }
        String customerNo = str(row.get("customer_no"));
        if (customerNo != null) fields.putIfAbsent("customerNo", customerNo);
        String branchCode = str(row.get("branch_code"));
        String branchName = str(row.get("_branch_name"));
        if (branchCode != null) { fields.put("branch", branchName != null ? branchName : branchCode); fields.put("branchCode", branchCode); }

        long id = ((Number) row.get("id")).longValue();
        String dbStatus = str(row.get("req_status"));
        Long creator = num(row.get("created_by"));
        Long editor = num(row.get("modified_by"));
        boolean pending = "PENDING".equals(dbStatus);
        boolean canAuthorise = who.admin() || staffCanAuthorise;

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", Long.toString(id));
        r.put("displayId", String.format("%s-%06d", m.prefix(), id));
        r.put("status", uiStatus(dbStatus));
        r.put("dbStatus", dbStatus);
        r.put("submittedOn", row.get("created_on") instanceof Timestamp t ? t.toInstant().atZone(IST).format(SUBMITTED) : "");
        r.put("createdBy", creator);
        r.put("verifiedBy", num(row.get("verified_by")));
        r.put("canVerify", pending && canAuthorise && !Objects.equals(creator, who.id()) && !Objects.equals(editor, who.id()));
        r.put("canEdit", "APPROVED".equals(dbStatus) || (pending && Objects.equals(creator, who.id())));
        r.put("fields", fields);
        return r;
    }

    private static String uiStatus(String db) {
        if (db == null) return "Pending";
        return switch (db) {
            case "APPROVED", "COMPLETED" -> "Authorised";
            case "REJECTED", "CANCELLED", "FAILED" -> "Rejected";
            default -> "Pending";
        };
    }

    private static List<String> dbStatuses(String s) {
        return switch (s.toUpperCase(Locale.ROOT)) {
            case "PENDING" -> List.of("PENDING");
            case "AUTHORISED", "AUTHORIZED" -> List.of("APPROVED", "COMPLETED");
            case "APPROVED" -> List.of("APPROVED");
            case "REJECTED" -> List.of("REJECTED", "CANCELLED", "FAILED");
            case "CANCELLED" -> List.of("CANCELLED");
            case "COMPLETED" -> List.of("COMPLETED");
            case "FAILED" -> List.of("FAILED");
            default -> List.of();
        };
    }

    private static String fmt(Object o) {
        if (o instanceof java.sql.Date d) return d.toLocalDate().toString();
        if (o instanceof Timestamp t) return t.toInstant().atZone(IST).toLocalDateTime().toString().replace('T', ' ');
        if (o instanceof BigDecimal b) return b.toPlainString();
        if (o instanceof Boolean b) return b ? "Yes" : "No";
        return String.valueOf(o);
    }

    private static String maskValue(String s) {
        return s.length() <= 4 ? s : "XXXX " + s.substring(s.length() - 4);
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }

    private static Long num(Object o) { return o instanceof Number n ? n.longValue() : null; }

    private static BigDecimal dec(Object o) {
        if (o == null) return null;
        return o instanceof BigDecimal b ? b : new BigDecimal(o.toString());
    }

    private static LocalDate toDate(Object o) {
        if (o instanceof java.sql.Date d) return d.toLocalDate();
        if (o instanceof LocalDate d) return d;
        return null;
    }

    private static String quoted(List<String> names) {
        return String.join(", ", names.stream().map(n -> "\"" + n + "\"").toList());
    }

    // ------------------------------------------------------------------ audit

    private void audit(Who who, HttpServletRequest http, String action, Module m, Long recordId,
                       String oldStatus, String newStatus, String detail) {
        if (!auditReady) return;
        db.update("insert into public.\"MB005001\" (actor_id, actor_name, actor_role, ip, action, module, record_id, old_status, new_status, detail) "
                        + "values (?,?,?,?,?,?,?,?,?,?)",
                who.id(), who.name(), who.role(), http.getRemoteAddr(), action, m.key(), recordId, oldStatus, newStatus,
                detail == null ? null : detail.substring(0, Math.min(detail.length(), 500)));
    }
}
