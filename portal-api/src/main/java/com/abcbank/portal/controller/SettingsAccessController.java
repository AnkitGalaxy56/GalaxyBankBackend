package com.abcbank.portal.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;

import java.util.*;

@RestController
@RequestMapping("/settings")
public class SettingsAccessController {
    private final JdbcTemplate db;
    private final ObjectMapper mapper;
    public SettingsAccessController(JdbcTemplate db, ObjectMapper mapper) { this.db=db; this.mapper=mapper; }

    @GetMapping("/roles")
    public List<Map<String,Object>> list(Authentication auth) {
        requireAdmin(auth);
        List<Map<String,Object>> rows=db.queryForList("select r.id,r.role_name,r.description,r.active,r.system_role,coalesce((select count(*) from public.\"MB001001\" u where u.user_type in ('STAFF','ADMIN') and (lower(coalesce(u.sys_role,''))=lower(r.role_name) or lower(coalesce(u.user_role,''))=lower(r.role_name))),0) assigned_users,coalesce(r.permissions,'[]'::jsonb)::text permissions from public.portal_access_roles r order by r.system_role desc,r.role_name");
        return rows.stream().map(this::role).toList();
    }

    @PostMapping("/roles") @Transactional
    public Map<String,Object> create(@RequestBody Map<String,Object> body, Authentication auth) {
        requireAdmin(auth); validate(body);
        String id=Objects.toString(body.getOrDefault("id","role-"+UUID.randomUUID()));
        try {
            db.update("insert into public.portal_access_roles(id,role_name,description,active,system_role,permissions,created_by,updated_by) values (?,?,?,?,?,?::jsonb,?,?)",
                    id, name(body), Objects.toString(body.get("description"),""), active(body), Boolean.TRUE.equals(body.get("systemRole")), json(body.get("permissions")), uid(auth), uid(auth));
        } catch (org.springframework.dao.DuplicateKeyException e) { throw new ResponseStatusException(HttpStatus.CONFLICT,"A role with this name already exists."); }
        return find(id);
    }

    @PutMapping("/roles/{id}") @Transactional
    public Map<String,Object> update(@PathVariable String id,@RequestBody Map<String,Object> body,Authentication auth) {
        requireAdmin(auth); validate(body);
        int n=db.update("update public.portal_access_roles set role_name=?,description=?,active=?,permissions=?::jsonb,updated_by=?,updated_at=now() where id=?",
                name(body),Objects.toString(body.get("description"),""),active(body),json(body.get("permissions")),uid(auth),id);
        if(n==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Role not found or system role cannot be edited.");
        return find(id);
    }

    @DeleteMapping("/roles/{id}") @Transactional
    public Map<String,String> delete(@PathVariable String id,Authentication auth) {
        requireAdmin(auth);
        Integer count=db.queryForObject("select count(*) from public.\"MB001001\" where lower(coalesce(sys_role,''))=lower((select role_name from public.portal_access_roles where id=?)) or lower(coalesce(user_role,''))=lower((select role_name from public.portal_access_roles where id=?))",Integer.class,id,id);
        if(count!=null&&count>0) throw new ResponseStatusException(HttpStatus.CONFLICT,"This role is assigned to users and cannot be deleted.");
        int n=db.update("delete from public.portal_access_roles where id=? and system_role=false",id);
        if(n==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Role not found or system role cannot be deleted.");
        return Map.of("message","Role deleted successfully.");
    }

    @GetMapping("/my-access")
    public Map<String,Object> myAccess(Authentication auth) {
        if(auth==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentication required.");
        String role = "Bank User";
        if (auth.getDetails() instanceof Map<?,?> claims) {
            String userType = Objects.toString(claims.get("role"), "STAFF").toUpperCase(Locale.ROOT);
            role = userType.equals("ADMIN") || userType.equals("SUPER_ADMIN")
                    ? "Super Admin" : Objects.toString(claims.get("userRole"), "Bank User");
        }
        if (role.equalsIgnoreCase("Super Admin")) return Map.of("roleName", role, "permissions", List.of(), "configured", false);
        List<Map<String,Object>> roles=db.queryForList("select id,role_name,active,coalesce(permissions,'[]'::jsonb)::text permissions from public.portal_access_roles where lower(role_name)=lower(?)",role);
        if(roles.isEmpty()) return Map.of("roleName",role,"permissions",List.of(),"configured",false);
        Map<String,Object> r=roles.get(0);
        List<Map<String,Object>> permissions = Boolean.TRUE.equals(r.get("active")) ? parsePermissions(Objects.toString(r.get("permissions"),"[]")) : List.of();
        return Map.of("roleName",role,"permissions",permissions,"configured",!permissions.isEmpty());
    }

    private Map<String,Object> find(String id) { return role(db.queryForMap("select r.id,r.role_name,r.description,r.active,r.system_role,coalesce((select count(*) from public.\"MB001001\" u where u.user_type in ('STAFF','ADMIN') and (lower(coalesce(u.sys_role,''))=lower(r.role_name) or lower(coalesce(u.user_role,''))=lower(r.role_name))),0) assigned_users,coalesce(r.permissions,'[]'::jsonb)::text permissions from public.portal_access_roles r where r.id=?",id)); }
    private Map<String,Object> role(Map<String,Object> r) {
        Map<String,Object> out=new LinkedHashMap<>(); out.put("id",r.get("id")); out.put("name",r.get("role_name")); out.put("description",r.get("description"));
        out.put("status",Boolean.TRUE.equals(r.get("active"))?"Active":"Inactive"); out.put("assignedUsers",((Number)r.get("assigned_users")).intValue()); out.put("systemRole",Boolean.TRUE.equals(r.get("system_role"))); out.put("permissions",parsePermissions(Objects.toString(r.get("permissions"),"[]"))); return out;
    }
    private List<Map<String,Object>> parsePermissions(String s) { try { return mapper.readValue(s,new TypeReference<List<Map<String,Object>>>(){}); } catch(Exception e){return List.of();} }
    private String json(Object value) { try { return mapper.writeValueAsString(value==null?List.of():value); } catch(Exception e){ throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid role permissions."); } }
    private void validate(Map<String,Object> b) { String n=name(b),d=Objects.toString(b.get("description"),"").trim(); if(n.length()<2||n.length()>60) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Role name must contain 2 to 60 characters."); if(d.length()<10||d.length()>250) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Description must contain 10 to 250 characters."); if(!(b.get("permissions") instanceof List<?>)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Select at least one screen permission."); }
    private String name(Map<String,Object> b){return Objects.toString(b.get("name"),"").trim();}
    private boolean active(Map<String,Object> b){return "Active".equalsIgnoreCase(Objects.toString(b.get("status"),"Active"));}
    private long uid(Authentication a){return Long.parseLong(a.getName());}
    private void requireAdmin(Authentication a){if(a==null||a.getAuthorities().stream().noneMatch(x->Set.of("ROLE_ADMIN","ROLE_SUPER_ADMIN").contains(x.getAuthority()))) throw new ResponseStatusException(a==null?HttpStatus.UNAUTHORIZED:HttpStatus.FORBIDDEN,"Administrator access required.");}
}
