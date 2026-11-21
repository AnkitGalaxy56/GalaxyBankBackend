package com.abcbank.portal.controller;

import com.abcbank.portal.requests.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Bank Master (MB002001) and Branch Master (MB002002).
 *
 *  - Everyone with a portal role can read; only ADMIN / SUPER_ADMIN can create or edit.
 *  - Only the fields listed below can be written; values are validated and bound as parameters.
 *  - The bank code / branch code is the primary key and cannot be changed after creation.
 *  - Banks and branches are never deleted (other tables reference them).
 *  - Every create and edit is written to the audit table MB005001.
 */
@RestController
public class MastersController {

    private static final Logger log = LoggerFactory.getLogger(MastersController.class);
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final Pattern CODE = Pattern.compile("^[A-Za-z0-9_-]{2,20}$");
    private static final Pattern IFSC = Pattern.compile("^[A-Z]{4}0[A-Z0-9]{6}$");

    private enum Kind { TEXT, TIME }

    /** One writable column: database column, key used by the web page, rules. */
    private record F(String column, String key, Kind kind, boolean required, int max, Pattern pattern,
                     String message, boolean upper, boolean immutable) {
        static F text(String column, String key, int max) { return new F(column, key, Kind.TEXT, false, max, null, null, false, false); }
        static F time(String column, String key) { return new F(column, key, Kind.TIME, false, 8, null, null, false, false); }
        F req() { return new F(column, key, kind, true, max, pattern, message, upper, immutable); }
        F re(Pattern p, String m) { return new F(column, key, kind, required, max, p, m, upper, immutable); }
        F up() { return new F(column, key, kind, required, max, pattern, message, true, immutable); }
        F imm() { return new F(column, key, kind, required, max, pattern, message, upper, true); }
    }

    private static final Pattern PIN = Pattern.compile("^[1-9][0-9]{5}$");

    private static final List<F> BANK = List.of(
            F.text("bank_code", "code", 20).req().up().imm().re(CODE, "Bank code may contain letters, digits, - and _ (2-20 characters)."),
            F.text("bank_name", "name", 150).req(),
            F.text("bank_type", "type", 30),
            F.text("rbi_license", "registrationLicense", 50),
            F.text("sponsor_bank", "sponsorBank", 20).up(),
            F.text("ifsc_code", "ifsc", 11).up().re(IFSC, "Enter a valid 11-character IFSC code (e.g. HDFC0001234)."),
            F.text("account_no", "sponsorAccountId", 30),
            F.text("pan_no", "pan", 10).up().re(Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]$"), "Enter a valid PAN (e.g. ABCDE1234F)."),
            F.text("gst_no", "gst", 15).up().re(Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$"), "Enter a valid GSTIN."),
            F.time("working_time_from", "workingFrom"),
            F.time("working_time_to", "workingTo"),
            F.text("micr_code", "micr", 9).re(Pattern.compile("^[0-9]{9}$"), "MICR code must be 9 digits."),
            // The dump names this column "address"; a database built from the sheet may call it "add". Whichever exists is used.
            F.text("address", "address", 150), F.text("add", "address", 150),
            F.text("add1", "address1", 150), F.text("add2", "address2", 150), F.text("add3", "address3", 150),
            F.text("city", "city", 80), F.text("state", "state", 80),
            F.text("pincode", "pincode", 6).re(PIN, "Enter a valid 6-digit pincode."),
            F.text("cut_off_timings", "cutOffTimings", 50));   // optional column (sql/02)

    private static final List<F> BRANCH = List.of(
            F.text("branch_code", "code", 20).req().up().imm().re(CODE, "Branch code may contain letters, digits, - and _ (2-20 characters)."),
            F.text("bank_code", "bankCode", 20).up().imm(),
            F.text("branch_name", "name", 150).req(),
            F.text("rbi_license", "registrationLicense", 50),
            F.text("micr_code", "micr", 9).re(Pattern.compile("^[0-9]{9}$"), "MICR code must be 9 digits."),
            F.text("ifsc_code", "ifsc", 11).req().up().re(IFSC, "Enter a valid 11-character IFSC code (e.g. HDFC0001234)."),
            F.text("br_add", "address", 150), F.text("br_add1", "address1", 150),
            F.text("br_add2", "address2", 150), F.text("br_add3", "address3", 150),
            F.text("br_city", "city", 80), F.text("br_state", "state", 80),
            F.text("br_pincode", "pincode", 6).re(PIN, "Enter a valid 6-digit pincode."),
            F.text("br_phone", "phone", 15).re(Pattern.compile("^[0-9+\\-\\s]{7,15}$"), "Enter a valid phone number."));  // optional column (sql/02)

    private final JdbcTemplate db;
    private volatile Map<String, Set<String>> columnCache;
    private volatile Boolean auditTable;

    public MastersController(JdbcTemplate db) { this.db = db; }

    // ================================================================== banks

    @GetMapping("/banks")
    public Map<String, Object> listBanks(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "50") int pageSize,
                                         @RequestParam(required = false) String search, Authentication auth) {
        Who caller = who(auth);
        Set<String> cols = columns("MB002001");
        int size = Math.min(Math.max(pageSize, 1), 200), pg = Math.max(page, 1);
        List<Object> args = new ArrayList<>();
        String where = searchClause(search, args, "b.bank_code", "b.bank_name", "b.bank_type", "b.city", "b.ifsc_code::text");
        if (!caller.admin()) { where += (where.isBlank() ? " where " : " and ") + "exists (select 1 from public.\"MB002002\" bx where bx.bank_code=b.bank_code and bx.branch_code=?)"; args.add(caller.branch()); }
        args.add(size);
        args.add((long) (pg - 1) * size);
        List<Map<String, Object>> rows = db.queryForList(
                "select b.*, (select count(*) from public.\"MB002002\" x where x.bank_code = b.bank_code) as _branches, count(*) over() as _total "
                        + "from public.\"MB002001\" b" + where + " order by b.bank_name limit ? offset ?", args.toArray());
        return page(rows, BANK, cols, pg, size, true);
    }

    @GetMapping("/banks/{code}")
    public Map<String, Object> getBank(@PathVariable String code, Authentication auth) {
        Who caller = who(auth);
        List<Object> args = new ArrayList<>(List.of(code));
        String scope = caller.admin() ? "" : " and exists (select 1 from public.\"MB002002\" bx where bx.bank_code=b.bank_code and bx.branch_code=?)";
        if (!caller.admin()) args.add(caller.branch());
        List<Map<String, Object>> rows = db.queryForList(
                "select b.*, (select count(*) from public.\"MB002002\" x where x.bank_code = b.bank_code) as _branches "
                        + "from public.\"MB002001\" b where b.bank_code = ?" + scope, args.toArray());
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "Bank not found.");
        return bankRecord(rows.get(0), columns("MB002001"));
    }

    @PostMapping("/banks")
    @Transactional
    public ResponseEntity<Map<String, Object>> createBank(@RequestBody JsonNode body, Authentication auth, HttpServletRequest http) {
        Who who = writer(auth);
        Set<String> cols = columns("MB002001");
        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, Object> v = parse(BANK, cols, body, true, errors);
        checkHours(v, errors);
        if (errors.isEmpty()) checkSponsor(v.get("sponsor_bank"), (String) v.get("bank_code"), errors);
        fail(errors);
        v.put("created_by", who.id());
        List<Map<String, Object>> rows = write("insert into public.\"MB002001\" (" + quoted(v.keySet()) + ") values ("
                        + String.join(",", Collections.nCopies(v.size(), "?")) + ")", v.values().toArray(), "MB002001", null);
        syncBankTheme(Objects.toString(v.get("bank_code")), Objects.toString(v.get("bank_name")), true);
        audit(who, http, "CREATE", "bank-master", "code=" + v.get("bank_code"));
        return ResponseEntity.status(HttpStatus.CREATED).body(bankRecord(rows.get(0), cols));
    }

    @PutMapping("/banks/{code}")
    @Transactional
    public Map<String, Object> updateBank(@PathVariable String code, @RequestBody JsonNode body, Authentication auth, HttpServletRequest http) {
        Who who = writer(auth);
        Set<String> cols = columns("MB002001");
        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, Object> v = parse(BANK, cols, body, false, errors);
        checkHours(v, errors);
        if (errors.isEmpty()) checkSponsor(v.get("sponsor_bank"), code, errors);
        if (v.containsKey("bank_name") && v.get("bank_name") == null) errors.put("name", "Bank name is required.");
        fail(errors);
        if (v.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "No editable fields were supplied.");
        List<Map<String, Object>> rows = update("MB002001", "bank_code", code, v, who);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "Bank not found.");
        String currentName = Objects.toString(v.getOrDefault("bank_name", rows.get(0).get("bank_name")), "");
        syncBankTheme(code, currentName, false);
        audit(who, http, "UPDATE", "bank-master", "code=" + code + " fields=" + String.join(",", v.keySet()));
        return bankRecord(rows.get(0), cols);
    }

    /** Keeps one theme record linked to each bank master record. Bank ID is bank_code in MB002001. */
    private void syncBankTheme(String bankCode, String bankName, boolean created) {
        db.execute("create table if not exists public.portal_bank_theme (code varchar(100) primary key, theme_json text not null, updated_at timestamptz default now())");
        db.execute("alter table public.portal_bank_theme add column if not exists bank_id varchar(100)");
        String themeCode = bankCode.trim();
        List<String> existing = db.query("select theme_json from public.portal_bank_theme where lower(code)=lower(?)", (rs, n) -> rs.getString(1), themeCode);
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String,Object> theme;
            if (!existing.isEmpty()) {
                theme = om.readValue(existing.get(0), Map.class);
                theme.put("code", themeCode);
                theme.put("name", bankName);
                theme.putIfAbsent("shortName", bankName);
                theme.putIfAbsent("tagline", "Secure digital banking");
                theme.putIfAbsent("borderRadius", 8);
                if (!theme.containsKey("mobileColors")) theme.put("mobileColors", theme.get("colors"));
            } else {
                Map<String,String> colors = new LinkedHashMap<>();
                colors.put("primary", "#055242"); colors.put("primaryDark", "#03352b"); colors.put("primaryTint", "#e1eae8");
                colors.put("background", "#F5F7FA"); colors.put("surface", "#FFFFFF"); colors.put("textPrimary", "#1A2433");
                colors.put("textSecondary", "#5B6778"); colors.put("border", "#E1E6ED"); colors.put("authPanel", "#03352b");
                theme = new LinkedHashMap<>(); theme.put("code", themeCode); theme.put("name", bankName);
                theme.put("shortName", bankName); theme.put("tagline", "Secure digital banking"); theme.put("colors", colors);
                theme.put("mobileColors", colors); theme.put("borderRadius", 8); theme.put("fontFamily", "Inter, Roboto, sans-serif");
            }
            String json = om.writeValueAsString(theme);
            db.update("insert into public.portal_bank_theme(code, bank_id, theme_json) values (?,?,?) " +
                    "on conflict (code) do update set bank_id=excluded.bank_id, theme_json=excluded.theme_json, updated_at=now()",
                    themeCode, themeCode, json);
        } catch (Exception e) { throw new IllegalStateException("Could not synchronize bank colour theme for " + bankCode, e); }
    }

    // ================================================================== branches

    @GetMapping("/branches")
    public Map<String, Object> listBranches(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) String search,
                                            @RequestParam(required = false) String status, Authentication auth) {
        Who caller = who(auth);
        Set<String> cols = columns("MB002002");
        int size = Math.min(Math.max(pageSize, 1), 200), pg = Math.max(page, 1);
        if ("inactive".equalsIgnoreCase(status)) {
            // The branch table has no status column: every branch is active.
            return Map.of("items", List.of(), "total", 0, "page", pg, "pageSize", size);
        }
        List<Object> args = new ArrayList<>();
        String where = searchClause(search, args, "b.branch_code", "b.branch_name", "b.ifsc_code::text", "b.br_city");
        if (!caller.admin()) { where += (where.isBlank() ? " where " : " and ") + "b.branch_code = ?"; args.add(caller.branch()); }
        args.add(size);
        args.add((long) (pg - 1) * size);
        List<Map<String, Object>> rows = db.queryForList(
                "select b.*, " + BRANCH_EXTRAS + ", count(*) over() as _total from public.\"MB002002\" b" + where
                        + " order by b.branch_name limit ? offset ?", args.toArray());
        return page(rows, BRANCH, cols, pg, size, false);
    }

    private static final String BRANCH_EXTRAS =
            "(select count(distinct a.customer_no) from public.\"MB002003\" a where a.branch_code = b.branch_code) as _customers, "
                    + "(select k.bank_name from public.\"MB002001\" k where k.bank_code = b.bank_code) as _bank_name";

    @GetMapping("/branches/{code}")
    public Map<String, Object> getBranch(@PathVariable String code, Authentication auth) {
        Who caller = who(auth);
        List<Object> args = new ArrayList<>(List.of(code));
        String scope = caller.admin() ? "" : " and b.branch_code = ?";
        if (!caller.admin()) args.add(caller.branch());
        List<Map<String, Object>> rows = db.queryForList(
                "select b.*, " + BRANCH_EXTRAS + " from public.\"MB002002\" b where b.branch_code = ?" + scope, args.toArray());
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "Branch not found.");
        return branchRecord(rows.get(0), columns("MB002002"));
    }

    @PostMapping("/branches")
    @Transactional
    public ResponseEntity<Map<String, Object>> createBranch(@RequestBody JsonNode body, Authentication auth, HttpServletRequest http) {
        Who who = writer(auth);
        Set<String> cols = columns("MB002002");
        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, Object> v = parse(BRANCH, cols, body, true, errors);
        if (errors.isEmpty()) {
            Object bank = v.get("bank_code");
            if (bank == null) {
                List<String> banks = db.query("select bank_code from public.\"MB002001\" order by bank_code limit 2", (rs, n) -> rs.getString(1));
                if (banks.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Create a bank in Bank Master first.");
                if (banks.size() > 1) errors.put("bankCode", "Select the bank this branch belongs to.");
                else v.put("bank_code", banks.get(0));
            } else if (count("select count(*) from public.\"MB002001\" where bank_code = ?", bank) == 0) {
                errors.put("bankCode", "Bank not found.");
            }
        }
        fail(errors);
        v.put("created_by", who.id());
        List<Map<String, Object>> rows = write("insert into public.\"MB002002\" (" + quoted(v.keySet()) + ") values ("
                        + String.join(",", Collections.nCopies(v.size(), "?")) + ")", v.values().toArray(), "MB002002", BRANCH_EXTRAS_CTE);
        audit(who, http, "CREATE", "branch-master", "code=" + v.get("branch_code"));
        return ResponseEntity.status(HttpStatus.CREATED).body(branchRecord(rows.get(0), cols));
    }

    @PutMapping("/branches/{code}")
    @Transactional
    public Map<String, Object> updateBranch(@PathVariable String code, @RequestBody JsonNode body, Authentication auth, HttpServletRequest http) {
        Who who = writer(auth);
        Set<String> cols = columns("MB002002");
        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, Object> v = parse(BRANCH, cols, body, false, errors);
        if (v.containsKey("branch_name") && v.get("branch_name") == null) errors.put("name", "Branch name is required.");
        if (v.containsKey("ifsc_code") && v.get("ifsc_code") == null) errors.put("ifsc", "IFSC code is required.");
        fail(errors);
        if (v.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "No editable fields were supplied.");
        List<Map<String, Object>> rows = update("MB002002", "branch_code", code, v, who);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "Branch not found.");
        audit(who, http, "UPDATE", "branch-master", "code=" + code + " fields=" + String.join(",", v.keySet()));
        return branchRecord(rows.get(0), cols);
    }

    private static final String BRANCH_EXTRAS_CTE =
            "(select count(distinct a.customer_no) from public.\"MB002003\" a where a.branch_code = r.branch_code) as _customers, "
                    + "(select k.bank_name from public.\"MB002001\" k where k.bank_code = r.bank_code) as _bank_name";

    // ================================================================== caller

    private record Who(long id, String name, String role, String branch) {
        boolean admin() { return role.equals("ADMIN") || role.equals("SUPER_ADMIN"); }
    }

    private Who who(Authentication auth) {
        if (auth == null || !(auth.getDetails() instanceof Map<?, ?> claims)) throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required.");
        long id;
        try { id = Long.parseLong(auth.getName()); } catch (NumberFormatException e) { throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid session."); }
        String role = Objects.toString(claims.get("role"), "").toUpperCase(Locale.ROOT);
        if (!Set.of("ADMIN", "SUPER_ADMIN", "STAFF", "USER").contains(role)) throw new ApiException(HttpStatus.FORBIDDEN, "Your role cannot access master data.");
        String branch = Objects.toString(claims.get("branch"), "").trim();
        if (!role.equals("ADMIN") && !role.equals("SUPER_ADMIN") && branch.isEmpty()) throw new ApiException(HttpStatus.FORBIDDEN, "Your user has no branch assigned.");
        return new Who(id, Objects.toString(claims.get("username"), ""), role, branch);
    }

    private Who writer(Authentication auth) {
        Who w = who(auth);
        if (!w.admin()) throw new ApiException(HttpStatus.FORBIDDEN, "Only Super Admin can change bank or branch master data.");
        return w;
    }

    // ================================================================== database helpers

    private Set<String> columns(String table) {
        Map<String, Set<String>> c = columnCache;
        if (c == null) {
            Map<String, Set<String>> tmp = new HashMap<>();
            db.query("select table_name, column_name from information_schema.columns where table_schema='public' and table_name in ('MB002001','MB002002')",
                    (RowCallbackHandler) rs -> tmp.computeIfAbsent(rs.getString(1), k -> new HashSet<>()).add(rs.getString(2)));
            if (!tmp.isEmpty()) columnCache = tmp;
            c = tmp;
        }
        Set<String> s = c.get(table);
        if (s == null || s.isEmpty()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "The " + table + " table is not available.");
        return s;
    }

    private long count(String sql, Object... args) {
        Long n = db.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private String searchClause(String search, List<Object> args, String... columns) {
        if (search == null || search.isBlank()) return "";
        String s = search.trim();
        if (s.length() > 60) throw new ApiException(HttpStatus.BAD_REQUEST, "Search text is too long.");
        String like = "%" + s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        List<String> parts = new ArrayList<>();
        for (String c : columns) { parts.add(c + " ilike ?"); args.add(like); }
        return " where (" + String.join(" or ", parts) + ")";
    }

    private static String quoted(Collection<String> names) {
        return String.join(", ", names.stream().map(n -> "\"" + n + "\"").toList());
    }

    /** Runs an insert and returns the stored row (with the display extras for branches). */
    private List<Map<String, Object>> write(String insertSql, Object[] args, String table, String extras) {
        try {
            String select = extras == null
                    ? "select r.*, (select count(*) from public.\"MB002002\" x where x.bank_code = r.bank_code) as _branches from ins r"
                    : "select r.*, " + extras + " from ins r";
            return db.queryForList("with ins as (" + insertSql + " returning *) " + select, args);
        } catch (DuplicateKeyException e) {
            throw duplicate(e);
        } catch (DataIntegrityViolationException e) {
            log.warn("Master data rejected by the database: {}", e.getMostSpecificCause().getMessage());
            throw new ApiException(HttpStatus.BAD_REQUEST, "Some values were rejected by the database rules. Please check the form.");
        }
    }

    private List<Map<String, Object>> update(String table, String keyColumn, String code, Map<String, Object> v, Who who) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        for (var e : v.entrySet()) { sets.add("\"" + e.getKey() + "\" = ?"); args.add(e.getValue()); }
        sets.add("\"modified_by\" = ?");
        args.add(who.id());
        sets.add("\"modified_on\" = now()");
        args.add(code);
        String extras = table.equals("MB002001")
                ? "(select count(*) from public.\"MB002002\" x where x.bank_code = r.bank_code) as _branches"
                : BRANCH_EXTRAS_CTE;
        try {
            return db.queryForList("with upd as (update public.\"" + table + "\" set " + String.join(", ", sets)
                    + " where \"" + keyColumn + "\" = ? returning *) select r.*, " + extras + " from upd r", args.toArray());
        } catch (DuplicateKeyException e) {
            throw duplicate(e);
        } catch (DataIntegrityViolationException e) {
            log.warn("Master data rejected by the database: {}", e.getMostSpecificCause().getMessage());
            throw new ApiException(HttpStatus.BAD_REQUEST, "Some values were rejected by the database rules. Please check the form.");
        }
    }

    private ApiException duplicate(DuplicateKeyException e) {
        String m = String.valueOf(e.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
        if (m.contains("ifsc")) return new ApiException(HttpStatus.CONFLICT, "This IFSC code is already used by another record.", Map.of("ifsc", "This IFSC code is already used."));
        return new ApiException(HttpStatus.CONFLICT, "A record with this code already exists.", Map.of("code", "This code already exists."));
    }

    // ================================================================== parsing

    private Map<String, Object> parse(List<F> fields, Set<String> cols, JsonNode body, boolean create, Map<String, String> errors) {
        if (body == null || !body.isObject()) throw new ApiException(HttpStatus.BAD_REQUEST, "Request body must be a JSON object.");
        Map<String, Object> out = new LinkedHashMap<>();
        for (F f : fields) {
            if (!cols.contains(f.column())) continue;
            if (!create && f.immutable()) continue;
            JsonNode n = body.get(f.key());
            if (n == null || n.isNull()) {
                if (create && f.required()) errors.putIfAbsent(f.key(), label(f) + " is required.");
                continue;
            }
            if (n.isContainerNode()) { errors.putIfAbsent(f.key(), label(f) + " has an invalid value."); continue; }
            String raw = n.asText().trim();
            if (raw.isEmpty()) {
                if (f.required()) errors.putIfAbsent(f.key(), label(f) + " is required.");
                else out.put(f.column(), null);          // explicit clear
                continue;
            }
            Object value = parseOne(f, raw, errors);
            if (value != null) out.put(f.column(), value);
        }
        return out;
    }

    private Object parseOne(F f, String raw, Map<String, String> errors) {
        String l = label(f);
        if (f.kind() == Kind.TIME) {
            try { return LocalTime.parse(raw); }
            catch (Exception e) { errors.putIfAbsent(f.key(), l + " must be a time (HH:MM)."); return null; }
        }
        String s = f.upper() ? raw.toUpperCase(Locale.ROOT) : raw;
        if (s.chars().anyMatch(c -> c < 0x20 || c == 0x7f) || s.indexOf('<') >= 0 || s.indexOf('>') >= 0) {
            errors.putIfAbsent(f.key(), l + " contains characters that are not allowed."); return null;
        }
        if (s.length() > f.max()) { errors.putIfAbsent(f.key(), l + " must be at most " + f.max() + " characters."); return null; }
        if (f.pattern() != null && !f.pattern().matcher(s).matches()) {
            errors.putIfAbsent(f.key(), f.message() != null ? f.message() : l + " is not valid."); return null;
        }
        return s;
    }

    private static String label(F f) {
        String k = f.key().replaceAll("([a-z])([A-Z])", "$1 $2");
        return Character.toUpperCase(k.charAt(0)) + k.substring(1);
    }

    private void checkHours(Map<String, Object> v, Map<String, String> errors) {
        if (v.get("working_time_from") instanceof LocalTime a && v.get("working_time_to") instanceof LocalTime b && !b.isAfter(a)) {
            errors.put("workingTo", "Working end time must be after start time.");
        }
    }

    private void checkSponsor(Object sponsor, String ownCode, Map<String, String> errors) {
        if (sponsor == null) return;
        if (sponsor.equals(ownCode)) { errors.put("sponsorBank", "A bank cannot be its own sponsor."); return; }
        if (count("select count(*) from public.\"MB002001\" where bank_code = ?", sponsor) == 0) {
            errors.put("sponsorBank", "Sponsor bank must be an existing bank code.");
        }
    }

    private void fail(Map<String, String> errors) {
        if (errors.isEmpty()) return;
        String summary = String.join(" ", errors.values().stream().limit(3).toList());
        throw new ApiException(HttpStatus.BAD_REQUEST, summary, errors);
    }

    // ================================================================== response shape

    private Map<String, Object> page(List<Map<String, Object>> rows, List<F> spec, Set<String> cols, int page, int size, boolean bank) {
        List<Map<String, Object>> items = new ArrayList<>();
        long total = 0;
        for (Map<String, Object> r : rows) {
            total = ((Number) r.get("_total")).longValue();
            items.add(bank ? bankRecord(r, cols) : branchRecord(r, cols));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        out.put("total", total);
        out.put("page", page);
        out.put("pageSize", size);
        return out;
    }

    private Map<String, Object> fieldsOf(List<F> spec, Set<String> cols, Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (F f : spec) {
            if (!cols.contains(f.column())) continue;
            Object v = row.get(f.column());
            if (v == null) { m.putIfAbsent(f.key(), ""); continue; }
            m.put(f.key(), v instanceof java.sql.Time t ? t.toLocalTime().format(HHMM) : v.toString());
        }
        return m;
    }

    private Map<String, Object> bankRecord(Map<String, Object> row, Set<String> cols) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.get("bank_code").toString());
        m.putAll(fieldsOf(BANK, cols, row));
        for (String k : List.of("type", "sponsorBank", "pan", "cutOffTimings", "address", "address1", "address2", "address3", "city", "state",
                "pincode", "sponsorAccountId", "registrationLicense", "ifsc", "gst", "workingFrom", "workingTo", "micr")) m.putIfAbsent(k, "");
        Object n = row.get("_branches");
        m.put("branches", n == null ? "0" : n.toString());
        return m;
    }

    private Map<String, Object> branchRecord(Map<String, Object> row, Set<String> cols) {
        Map<String, Object> f = fieldsOf(BRANCH, cols, row);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.get("branch_code").toString());
        m.put("name", f.getOrDefault("name", ""));
        m.put("code", f.getOrDefault("code", ""));
        m.put("ifsc", f.getOrDefault("ifsc", ""));
        m.put("address", f.getOrDefault("address", ""));
        m.put("city", f.getOrDefault("city", ""));
        m.put("pincode", f.getOrDefault("pincode", ""));
        m.put("phone", f.getOrDefault("phone", ""));
        m.put("customers", row.get("_customers") instanceof Number n ? n.longValue() : 0L);
        m.put("status", "active");
        m.put("bankCode", f.getOrDefault("bankCode", ""));
        m.put("bankName", Objects.toString(row.get("_bank_name"), ""));
        return m;
    }

    // ================================================================== audit (MB005001)

    private void audit(Who who, HttpServletRequest http, String action, String module, String detail) {
        Boolean exists = auditTable;
        if (exists == null) {
            exists = db.queryForObject("select to_regclass('public.\"MB005001\"') is not null", Boolean.class);
            auditTable = exists;
        }
        if (!Boolean.TRUE.equals(exists)) return;
        db.update("insert into public.\"MB005001\" (actor_id, actor_name, actor_role, ip, action, module, detail) values (?,?,?,?,?,?,?)",
                who.id(), who.name(), who.role(), http.getRemoteAddr(), action, module,
                detail.substring(0, Math.min(detail.length(), 500)));
    }
}
