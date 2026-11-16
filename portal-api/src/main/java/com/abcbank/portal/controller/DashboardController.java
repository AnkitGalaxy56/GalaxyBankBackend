package com.abcbank.portal.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Dashboard data. The database is remote, so every round trip is expensive:
 * this controller uses a handful of combined queries (UNION ALL), caches table
 * metadata for the life of the process, and caches each result for 30 seconds.
 */
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private static final Map<String, String> REQUEST_TABLES = new LinkedHashMap<>();
    static {
        REQUEST_TABLES.put("RTGS/NEFT/IMPS", "MB004001");
        REQUEST_TABLES.put("Cheque book", "MB004002");
        REQUEST_TABLES.put("Stop payment", "MB004003");
        REQUEST_TABLES.put("FD opening", "MB004004");
        REQUEST_TABLES.put("RD opening", "MB004005");
        REQUEST_TABLES.put("Loan EMI payment", "MB004006");
        REQUEST_TABLES.put("Locker application", "MB004007");
        REQUEST_TABLES.put("Standing instruction", "MB004008");
        REQUEST_TABLES.put("Fresh loan application", "MB004010");
    }

    private static final long CACHE_MS = 30_000;

    private record Cached(long at, Map<String, Object> data) {}

    private final JdbcTemplate db;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private volatile Map<String, Set<String>> columnCache;

    /** The three dashboard queries are independent, so they run at the same time (one network round trip, not three). */
    private final ExecutorService pool = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "dashboard-query");
        t.setDaemon(true);
        return t;
    });

    public DashboardController(JdbcTemplate db) { this.db = db; }

    /** One query for the columns of all request tables, cached after the first success. */
    private Map<String, Set<String>> columns() {
        Map<String, Set<String>> c = columnCache;
        if (c != null) return c;
        Map<String, Set<String>> m = new HashMap<>();
        String in = String.join(",", Collections.nCopies(REQUEST_TABLES.size(), "?"));
        db.query("select table_name, column_name from information_schema.columns " +
                        "where table_schema='public' and table_name in (" + in + ")",
                (RowCallbackHandler) rs -> m.computeIfAbsent(rs.getString(1), k -> new HashSet<>()).add(rs.getString(2)),
                REQUEST_TABLES.values().toArray());
        if (!m.isEmpty()) columnCache = m;
        return m;
    }

    private Map<String, Object> stat(String key, String label, long value, String helper, String tone, String icon) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key); m.put("label", label); m.put("value", String.format("%,d", value));
        if (helper != null) m.put("helper", helper);
        if (tone != null) m.put("helperTone", tone);
        m.put("icon", icon);
        return m;
    }

    private String uiStatus(String s) {
        String v = s == null ? "" : s.toUpperCase(Locale.ROOT);
        if (v.contains("REJECT")) return "rejected";
        if (v.contains("COMPLETE") || v.contains("APPROV") || v.contains("AUTHORI") || v.contains("SUCCESS")) return "completed";
        if (v.contains("REVIEW") || v.contains("PROCESS")) return "in_review";
        return "new";
    }

    private String customerColumn(Set<String> cols) {
        for (String c : List.of("customer_id", "cust_id", "customer_no", "cif_no", "customer_name")) {
            if (cols.contains(c)) return c;
        }
        for (String c : cols) {
            if (c.contains("cust")) return c;
        }
        return null;
    }

    @GetMapping("/bank")
    public Map<String, Object> bank(@RequestParam(required = false) String branch, Authentication auth) {
        String role = "";
        String userBranch = "";
        if (auth != null && auth.getDetails() instanceof Map<?, ?> d) {
            role = Objects.toString(d.get("role"), "");
            userBranch = Objects.toString(d.get("branch"), "");
        }
        // Non-admins are always limited to their own branch.
        final String effectiveBranch = "ADMIN".equalsIgnoreCase(role)
                ? (branch == null || branch.isBlank() ? null : branch)
                : (userBranch.isBlank() ? null : userBranch);

        String key = effectiveBranch == null ? "*" : effectiveBranch;
        Cached hit = cache.get(key);
        if (hit != null && System.currentTimeMillis() - hit.at() < CACHE_MS) return hit.data();

        Map<String, Set<String>> cols = columns();

        StringBuilder aggSql = new StringBuilder();
        List<Object> aggArgs = new ArrayList<>();
        StringBuilder recentSql = new StringBuilder();
        List<Object> recentArgs = new ArrayList<>();

        for (var e : REQUEST_TABLES.entrySet()) {
            Set<String> c = cols.get(e.getValue());
            if (c == null || c.isEmpty()) continue;
            String t = "public.\"" + e.getValue() + "\"";
            boolean scoped = effectiveBranch != null && c.contains("branch_code");
            String where = scoped ? " where branch_code=?" : "";
            String label = e.getKey(); // constant from REQUEST_TABLES, contains no quotes

            String pending = c.contains("req_status")
                    ? "count(*) filter (where upper(req_status::text) in ('NEW','PENDING','SUBMITTED','IN_REVIEW'))"
                    : "cast(0 as bigint)";
            if (aggSql.length() > 0) aggSql.append(" union all ");
            aggSql.append("select '").append(label).append("' as label, count(*) as total, ")
                    .append(pending).append(" as pending from ").append(t).append(where);
            if (scoped) aggArgs.add(effectiveBranch);

            if (c.contains("id")) {
                String cust = customerColumn(c);
                String order = c.contains("created_on") ? "created_on" : "id";
                if (recentSql.length() > 0) recentSql.append(" union all ");
                recentSql.append("(select '").append(label).append("' as type, id::text as id, ")
                        .append(c.contains("branch_code") ? "branch_code::text" : "''::text").append(" as branch, ")
                        .append(c.contains("created_on") ? "created_on::text" : "''::text").append(" as submitted, ")
                        .append(c.contains("req_status") ? "req_status::text" : "''::text").append(" as status, ")
                        .append(cust != null ? cust + "::text" : "''::text").append(" as customer")
                        .append(" from ").append(t).append(where)
                        .append(" order by ").append(order).append(" desc limit 5)");
                if (scoped) recentArgs.add(effectiveBranch);
            }
        }

        final String aggQuery = aggSql.toString();
        final String recentQuery = recentSql.toString();
        CompletableFuture<List<Map<String, Object>>> aggFuture = CompletableFuture.supplyAsync(
                () -> aggQuery.isEmpty() ? List.<Map<String, Object>>of() : db.queryForList(aggQuery, aggArgs.toArray()), pool);
        CompletableFuture<List<Map<String, Object>>> recentFuture = CompletableFuture.supplyAsync(
                () -> recentQuery.isEmpty() ? List.<Map<String, Object>>of() : db.queryForList(recentQuery, recentArgs.toArray()), pool);
        Map<String, Long> usersByBranch = new TreeMap<>();
        CompletableFuture<Void> usersFuture = CompletableFuture.runAsync(() ->
                db.query("select coalesce(branch,'') as branch, count(*) from public.\"MB001001\" group by 1",
                        (RowCallbackHandler) rs -> usersByBranch.put(rs.getString(1), rs.getLong(2))), pool);
        CompletableFuture.allOf(aggFuture, recentFuture, usersFuture).join();

        long total = 0, pendingTotal = 0;
        List<Map<String, Object>> byType = new ArrayList<>();
        if (aggSql.length() > 0) {
            for (Map<String, Object> r : aggFuture.join()) {
                long n = ((Number) r.get("total")).longValue();
                total += n;
                pendingTotal += ((Number) r.get("pending")).longValue();
                if (n > 0) byType.add(new LinkedHashMap<>(Map.of("label", r.get("label"), "count", n)));
            }
        }

        List<Map<String, Object>> recent = new ArrayList<>();
        if (recentSql.length() > 0) {
            for (Map<String, Object> r : recentFuture.join()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", Objects.toString(r.get("id"), ""));
                row.put("customer", Objects.toString(r.get("customer"), ""));
                row.put("type", Objects.toString(r.get("type"), ""));
                row.put("branch", Objects.toString(r.get("branch"), ""));
                row.put("submitted", Objects.toString(r.get("submitted"), ""));
                row.put("status", uiStatus(Objects.toString(r.get("status"), "")));
                recent.add(row);
            }
            recent.sort((a, b) -> Objects.toString(b.get("submitted")).compareTo(Objects.toString(a.get("submitted"))));
            if (recent.size() > 10) recent = new ArrayList<>(recent.subList(0, 10));
        }

        // One query gives both the branch list and the user count.
        List<String> branches = usersByBranch.keySet().stream().filter(b -> !b.isBlank()).toList();
        long users = effectiveBranch == null
                ? usersByBranch.values().stream().mapToLong(Long::longValue).sum()
                : usersByBranch.getOrDefault(effectiveBranch, 0L);

        byType.sort((a, b) -> Long.compare((Long) b.get("count"), (Long) a.get("count")));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("branches", branches);
        out.put("stats", List.of(
                stat("branches", "Branches", branches.size(), null, null, "branches"),
                stat("users", "Portal users", users, null, null, "users"),
                stat("pending", "Pending requests", pendingTotal, null, pendingTotal > 0 ? "warning" : "success", "requests"),
                stat("requests", "Total requests", total, null, null, "requests")));
        out.put("recentRequests", recent);
        out.put("leadEnquiries", byType.size() > 5 ? new ArrayList<>(byType.subList(0, 5)) : byType);

        cache.put(key, new Cached(System.currentTimeMillis(), out));
        return out;
    }

    @GetMapping("/super-admin")
    public Map<String, Object> superAdmin() {
        Cached hit = cache.get("#super");
        if (hit != null && System.currentTimeMillis() - hit.at() < CACHE_MS) return hit.data();

        Map<String, Set<String>> cols = columns();
        StringBuilder sql = new StringBuilder("select (select count(*) from public.\"MB001001\") as users");
        List<String> parts = new ArrayList<>();
        for (String t : REQUEST_TABLES.values()) {
            if (cols.containsKey(t)) parts.add("(select count(*) from public.\"" + t + "\")");
        }
        sql.append(", ").append(parts.isEmpty() ? "cast(0 as bigint)" : String.join(" + ", parts)).append(" as requests");
        Map<String, Object> r = db.queryForMap(sql.toString());
        long users = ((Number) r.get("users")).longValue();
        long total = ((Number) r.get("requests")).longValue();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stats", List.of(
                stat("banks", "No. of banks", 1, null, null, "banks"),
                stat("users", "No. of application users", users, null, null, "users"),
                stat("requests", "No. of requests", total, null, null, "requests")));
        out.put("recentBanks", List.of());
        out.put("requestsByBank", List.of());
        cache.put("#super", new Cached(System.currentTimeMillis(), out));
        return out;
    }
}
