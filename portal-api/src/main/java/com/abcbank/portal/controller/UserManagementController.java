package com.abcbank.portal.controller;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;

import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/user-management")
public class UserManagementController {
    private final JdbcTemplate db;
    private final PasswordEncoder passwords;

    public UserManagementController(JdbcTemplate db, PasswordEncoder passwords) {
        this.db = db;
        this.passwords = passwords;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "1000") int pageSize,
                                    @RequestParam(required = false) String search,
                                    @RequestParam(required = false) String branchId,
                                    @RequestParam(required = false) String role,
                                    @RequestParam(required = false) String authorizationStatus,
                                    Authentication auth) {
        requireAdminOrMaker(auth);
        int safePage = Math.max(1, page), safeSize = Math.min(5000, Math.max(1, pageSize));
        String q = search == null ? "" : search.trim();
        StringBuilder where = new StringBuilder(" where user_type in ('STAFF','ADMIN') ");
        List<Object> args = new ArrayList<>();
        if (isBankAdminMaker(auth)) { where.append(" and branch = ? and lower(user_role) not like '%admin%' and user_type = 'STAFF' "); args.add(callerBranch(auth)); }
        if (!q.isBlank()) {
            where.append(" and (coalesce(emp_no,'') ilike ? or coalesce(full_name,'') ilike ? or coalesce(user_name,'') ilike ? or coalesce(email,'') ilike ? or coalesce(branch,'') ilike ?) ");
            String like = "%" + q + "%"; for (int i=0;i<5;i++) args.add(like);
        }
        if (branchId != null && !branchId.isBlank() && !"all".equalsIgnoreCase(branchId)) { where.append(" and branch = ? "); args.add(branchId); }
        if (role != null && !role.isBlank() && !"all".equalsIgnoreCase(role)) { where.append(" and lower(user_role) = lower(?) "); args.add(role); }
        if (authorizationStatus != null && !authorizationStatus.isBlank() && !"all".equalsIgnoreCase(authorizationStatus)) {
            where.append(" and ((? ilike 'Authorized' and verified_on is not null) or (? ilike 'Pending' and verified_on is null)) "); args.add(authorizationStatus); args.add(authorizationStatus);
        }
        Integer total = db.queryForObject("select count(*) from public.\"MB001001\"" + where, Integer.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args); pageArgs.add(safeSize); pageArgs.add((safePage-1)*safeSize);
        List<Map<String,Object>> rows = db.queryForList("select user_id,emp_no,title,user_type,first_name,middle_name,last_name,full_name,designation,dob,email,mobile,user_name,disp_name,user_role,sys_role,branch,(select b.branch_name from public.\"MB002002\" b where b.branch_code=public.\"MB001001\".branch) as branch_name,status,multi_branch,idle_timeout,bad_login,login_time,logout_time,inop_date,\"2fa_login\",authentication,verified_on from public.\"MB001001\"" + where + " order by user_id desc limit ? offset ?", pageArgs.toArray());
        List<Map<String,Object>> items = rows.stream().map(this::toUser).toList();
        return Map.of("items", items, "total", total == null ? 0 : total, "page", safePage, "pageSize", safeSize);
    }

    @GetMapping("/{id}")
    public Map<String,Object> get(@PathVariable long id, Authentication auth) {
        requireAdminOrMaker(auth);
        if (isBankAdminMaker(auth) && !existsInBranch(id, callerBranch(auth))) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Outside your branch scope.");
        return toUser(db.queryForMap("select user_id,emp_no,title,user_type,first_name,middle_name,last_name,full_name,designation,dob,email,mobile,user_name,disp_name,user_role,sys_role,branch,(select b.branch_name from public.\"MB002002\" b where b.branch_code=public.\"MB001001\".branch) as branch_name,status,multi_branch,idle_timeout,bad_login,login_time,logout_time,inop_date,\"2fa_login\",authentication,verified_on from public.\"MB001001\" where user_id=? and user_type in ('STAFF','ADMIN')", id));
    }

    @PostMapping
    @Transactional
    public Map<String,Object> create(@RequestBody Map<String,Object> body, Authentication auth) {
        requireAdminOrMaker(auth);
        if (isBankAdminMaker(auth)) { body.put("role", "Bank User"); body.put("systemRole", "Maker"); body.put("branchId", callerBranch(auth)); body.put("multiBranchAccess", false); }
        validate(body, null);
        String selectedRole = role(body);
        String userType = "Super Admin".equalsIgnoreCase(selectedRole) ? "ADMIN" : "STAFF";
        long id = db.queryForObject("insert into public.\"MB001001\" (emp_no,title,user_type,first_name,middle_name,last_name,designation,dob,email,mobile,user_name,disp_name,user_role,sys_role,branch,status,multi_branch,idle_timeout,bad_login,\"2fa_login\",authentication,created_by) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,?,?,?) returning user_id",
                Long.class, val(body,"employeeId"), titleDb(body), userType, val(body,"firstName"), emptyNull(val(body,"middleName")), val(body,"lastName"), emptyNull(val(body,"twoFactorDesignation")), dateOrNull(val(body,"dateOfBirth")), emptyNull(val(body,"email")), emptyNull(val(body,"mobile")), val(body,"userName"), emptyNull(val(body,"displayName")), role(body), emptyNull(val(body,"systemRole")), emptyNull(val(body,"branchId")), "PENDING", bool(body,"multiBranchAccess"), intOrNull(body.get("idleTimeoutSeconds")), bool(body,"twoFactorLogin"), emptyNull(val(body,"authenticationType")), userId(auth));
        return get(id, auth);
    }

    @PutMapping("/{id}")
    @Transactional
    public Map<String,Object> update(@PathVariable long id, @RequestBody Map<String,Object> body, Authentication auth) {
        requireAdmin(auth); validate(body, id);
        int changed = db.update("update public.\"MB001001\" set emp_no=?,title=?,user_type=?,first_name=?,middle_name=?,last_name=?,designation=?,dob=?,email=?,mobile=?,user_name=?,disp_name=?,user_role=?,sys_role=?,branch=?,status=?,multi_branch=?,idle_timeout=?,bad_login=?,inop_date=?,\"2fa_login\"=?,authentication=?,modified_by=?,modified_on=now() where user_id=? and user_type in ('STAFF','ADMIN')",
                val(body,"employeeId"), titleDb(body), "Super Admin".equalsIgnoreCase(role(body)) ? "ADMIN" : "STAFF", val(body,"firstName"), emptyNull(val(body,"middleName")), val(body,"lastName"), emptyNull(val(body,"twoFactorDesignation")), dateOrNull(val(body,"dateOfBirth")), emptyNull(val(body,"email")), emptyNull(val(body,"mobile")), val(body,"userName"), emptyNull(val(body,"displayName")), role(body), emptyNull(val(body,"systemRole")), emptyNull(val(body,"branchId")), statusDb(val(body,"status")), bool(body,"multiBranchAccess"), intOrNull(body.get("idleTimeoutSeconds")), intOrNull(body.get("badLoginCount")), dateOrNull(val(body,"inoperativeDate")), bool(body,"twoFactorLogin"), emptyNull(val(body,"authenticationType")), userId(auth), id);
        if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found.");
        return get(id, auth);
    }

    @PatchMapping("/{id}/authorize")
    @Transactional
    public Map<String,Object> authorize(@PathVariable long id, Authentication auth) {
        requireAdmin(auth);
        int n = db.update("update public.\"MB001001\" set status='ACTIVE',verified_by=?,verified_on=now(),modified_by=?,modified_on=now() where user_id=? and user_type in ('STAFF','ADMIN') and status='PENDING'", userId(auth), userId(auth), id);
        if (n == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending users can be authorised.");
        return get(id, auth);
    }

    @PatchMapping("/{id}/status")
    public Map<String,Object> status(@PathVariable long id, @RequestBody Map<String,String> body, Authentication auth) {
        requireAdmin(auth);
        String status = Objects.toString(body.get("status"), "");
        String dbStatus = switch (status.toLowerCase(Locale.ROOT)) {
            case "operative", "active" -> "ACTIVE";
            case "entered", "pending" -> "PENDING";
            case "in-operative", "closed" -> "CLOSED";
            case "suspended", "password blocked", "locked" -> "LOCKED";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported user status.");
        };
        int n = db.update("update public.\"MB001001\" set status=?,modified_by=?,modified_on=now() where user_id=? and user_type in ('STAFF','ADMIN')", dbStatus, userId(auth), id);
        if (n == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found.");
        return get(id, auth);
    }

    @PostMapping("/{id}/assign-password")
    @Transactional
    public Map<String,String> assignPassword(@PathVariable long id, @RequestBody Map<String,String> body, Authentication auth) {
        requireAdmin(auth);
        String password = body.get("password"); validatePassword(password);
        Integer exists = db.queryForObject("select count(*) from public.\"MB001001\" where user_id=? and user_type in ('STAFF','ADMIN')", Integer.class, id);
        if (exists == null || exists == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found.");
        db.update("insert into public.\"MB001002\" (user_id,password_hash,must_reset,created_by) values (?,?,true,?)", id, passwords.encode(password), userId(auth));
        db.update("update public.\"MB001001\" set bad_login=0,modified_by=?,modified_on=now() where user_id=? and user_type in ('STAFF','ADMIN')", userId(auth), id);
        return Map.of("message", "Password assigned successfully.");
    }

    private Map<String,Object> toUser(Map<String,Object> r) {
        String status = switch (Objects.toString(r.get("status"), "PENDING")) {
            case "ACTIVE" -> "Operative"; case "LOCKED" -> ((Number)r.getOrDefault("bad_login", 0)).intValue() >= 5 ? "Password Blocked" : "Suspended"; case "CLOSED" -> "In-Operative"; default -> "Entered";
        };
        String dbRole = Objects.toString(r.get("user_role"), "STAFF");
        String role = "ADMIN".equalsIgnoreCase(Objects.toString(r.get("user_type"), "")) ? "Super Admin"
                : (dbRole.toLowerCase(Locale.ROOT).contains("admin") ? "Bank Admin" : "Bank User");
        String name = Objects.toString(r.get("full_name"), "").trim();
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("id", Objects.toString(r.get("user_id"))); out.put("title", "Mr.".equals(Objects.toString(r.get("title"), "")) ? "Mr" : "Dr.".equals(Objects.toString(r.get("title"), "")) ? "Dr" : Objects.toString(r.get("title"), "Mr"));
        out.put("employeeId", Objects.toString(r.get("emp_no"), "")); out.put("firstName", Objects.toString(r.get("first_name"), ""));
        out.put("middleName", Objects.toString(r.get("middle_name"), "")); out.put("lastName", Objects.toString(r.get("last_name"), "")); out.put("name", name);
        out.put("mobile", Objects.toString(r.get("mobile"), "")); out.put("email", Objects.toString(r.get("email"), "")); out.put("userName", Objects.toString(r.get("user_name"), ""));
        out.put("displayName", Objects.toString(r.get("disp_name"), "")); out.put("role", role); out.put("systemRole", Objects.toString(r.get("sys_role"), ""));
        out.put("branchId", Objects.toString(r.get("branch"), "")); out.put("branchName", Objects.toString(r.get("branch_name"), Objects.toString(r.get("branch"), "")));
        out.put("multiBranchAccess", Boolean.TRUE.equals(r.get("multi_branch")) ? List.of(Objects.toString(r.get("branch"), "")) : List.of());
        out.put("status", status); out.put("authorizationStatus", r.get("verified_on") == null ? "Pending" : "Authorized");
        out.put("dateOfBirth", r.get("dob") == null ? "" : r.get("dob").toString()); out.put("twoFactorDesignation", Objects.toString(r.get("designation"), ""));
        out.put("idleTimeoutSeconds", r.get("idle_timeout") == null ? 1800 : ((Number)r.get("idle_timeout")).intValue()); out.put("badLoginCount", r.get("bad_login") == null ? 0 : ((Number)r.get("bad_login")).intValue());
        out.put("loginTime", timeOnly(r.get("login_time"))); out.put("logoutTime", timeOnly(r.get("logout_time"))); out.put("inoperativeDate", Objects.toString(r.get("inop_date"), ""));
        out.put("twoFactorLogin", Boolean.TRUE.equals(r.get("2fa_login"))); out.put("authenticationType", Objects.toString(r.get("authentication"), ""));
        out.put("accessLevel", Map.of("create",false,"modify",false,"view",true,"authorise",false)); out.put("lastLogin", Objects.toString(r.get("login_time"), "Never"));
        return out;
    }

    private void validate(Map<String,Object> b, Long id) {
        for (String k : List.of("employeeId","firstName","lastName","userName")) if (val(b,k).isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, k + " is required.");
        String selectedRole=role(b); if (!Set.of("Super Admin","Bank Admin","Bank User").contains(selectedRole)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Select a valid user role."); String sys=val(b,"systemRole"); if (!Set.of("Admin","Maker","Checker","no").contains(sys)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Select a valid system role."); if ("Super Admin".equals(selectedRole) && !"Admin".equalsIgnoreCase(sys)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Super Admin must use system role Admin."); if (!"Super Admin".equals(selectedRole) && !Set.of("Maker","Checker").contains(sys)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Staff users must use Maker or Checker.");
        String title = val(b,"title"); if (!Set.of("Mr","Mr.","Mrs","Miss","Ms","Dr.").contains(title)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid title.");
        String email = val(b,"email"); if (!email.isBlank() && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a valid email address.");
        String mobile = val(b,"mobile"); if (!mobile.isBlank() && !mobile.matches("^[6-9][0-9]{9}$")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a valid 10-digit mobile number.");
        Integer duplicate = db.queryForObject("select count(*) from public.\"MB001001\" where (emp_no=? or user_name=? or (email is not null and email=?)) and user_type in ('STAFF','ADMIN') and (?::bigint is null or user_id<>?)", Integer.class, val(b,"employeeId"), val(b,"userName"), email, id, id);
        if (duplicate != null && duplicate > 0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Employee ID, username or email is already in use.");
    }
    private boolean isBankAdminMaker(Authentication a) {
        if (a == null || !(a.getDetails() instanceof Map<?, ?> claims)) return false;
        String userRole = Objects.toString(claims.get("userRole"), "").toLowerCase(Locale.ROOT);
        String sysRole = Objects.toString(claims.get("sysRole"), "").toLowerCase(Locale.ROOT);
        String role = Objects.toString(claims.get("role"), "").toUpperCase(Locale.ROOT);
        return (role.equals("STAFF") || role.equals("USER")) && userRole.contains("admin") && sysRole.equals("maker");
    }
    private String callerBranch(Authentication a) {
        if (a == null || !(a.getDetails() instanceof Map<?, ?> claims)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required.");
        String branch = Objects.toString(claims.get("branch"), "").trim();
        if (branch.isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Branch assignment is required.");
        return branch;
    }
    private boolean existsInBranch(long id, String branch) {
        Integer count = db.queryForObject("select count(*) from public.\"MB001001\" where user_id=? and branch=? and user_type='STAFF' and lower(user_role) not like '%admin%'", Integer.class, id, branch);
        return count != null && count > 0;
    }
    private void requireAdminOrMaker(Authentication a) {
        if (isBankAdminMaker(a)) return;
        requireAdmin(a);
    }
    private void requireAdmin(Authentication a) { if (a == null || a.getDetails() == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentication required."); boolean allowed = a.getAuthorities().stream().anyMatch(x -> x.getAuthority().equals("ROLE_SUPER_ADMIN") || x.getAuthority().equals("ROLE_ADMIN")); if (!allowed) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Super Admin access required."); }
    private long userId(Authentication a) { return Long.parseLong(a.getName()); }
    private static String val(Map<String,Object> b,String k) { return Objects.toString(b.get(k),"").trim(); }
    private static String role(Map<String,Object> b) { String value=val(b,"role"); if(value.equalsIgnoreCase("Super Admin") || value.equalsIgnoreCase("SUPER_ADMIN")) return "Super Admin"; if(value.equalsIgnoreCase("Bank Admin") || value.equalsIgnoreCase("ADMIN_USER")) return "Bank Admin"; return "Bank User"; }
    private static String titleDb(Map<String,Object> b) { String t=val(b,"title"); return "Mr".equalsIgnoreCase(t)?"Mr.":"Dr".equalsIgnoreCase(t)?"Dr.":t; }
    private static String statusDb(String s) { return switch(s.toLowerCase(Locale.ROOT)) { case "operative", "active" -> "ACTIVE"; case "in-operative", "closed" -> "CLOSED"; case "suspended", "password blocked", "locked" -> "LOCKED"; default -> "PENDING"; }; }
    private static String timeOnly(Object value) { if (value instanceof java.sql.Timestamp t) return t.toLocalDateTime().toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")); return ""; }
    private static Boolean bool(Map<String,Object> b,String k) { Object v=b.get(k); if(v instanceof Boolean x)return x; if(v instanceof List<?> l)return !l.isEmpty(); return "true".equalsIgnoreCase(Objects.toString(v,""))||"yes".equalsIgnoreCase(Objects.toString(v,"")); }
    private static Integer intOrNull(Object o) { try { return o==null?null:Integer.valueOf(o.toString()); } catch(Exception e){return null;} }
    private static String emptyNull(String s) { return s==null||s.isBlank()?null:s; }
    private static Date dateOrNull(String s) { return s==null||s.isBlank()?null:Date.valueOf(LocalDate.parse(s)); }
    private static void validatePassword(String p) { if(p==null||p.length()<8||!p.matches(".*[A-Z].*")||!p.matches(".*[a-z].*")||!p.matches(".*[0-9].*")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Password must contain at least 8 characters, uppercase, lowercase and a number."); }
}
