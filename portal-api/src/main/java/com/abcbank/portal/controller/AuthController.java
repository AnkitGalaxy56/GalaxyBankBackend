package com.abcbank.portal.controller;

import com.abcbank.portal.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController @RequestMapping("/auth")
public class AuthController {
 private final JdbcTemplate db; private final PasswordEncoder passwords; private final JwtService jwt;
 @Value("${portal.otp.value}") private String otpValue;
 @Value("${portal.otp.ttl-minutes:5}") private long otpTtl;
 private record Pending(long userId,String username,String role,String branch,String name,String email,String phone,Integer idleTimeout,Instant expires){}
 private final Map<String,Pending> pending=new ConcurrentHashMap<>();
 public AuthController(JdbcTemplate db,PasswordEncoder passwords,JwtService jwt){this.db=db;this.passwords=passwords;this.jwt=jwt;}
 @PostMapping("/login") public Map<String,Object> login(@RequestBody Map<String,String> body){
  String username=body.getOrDefault("loginId",body.get("user_name")); String password=body.get("password");
  if(username==null||password==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Login ID and password are required");
  List<Map<String,Object>> users=db.queryForList("select user_id,user_name,user_role,user_type,branch,status,bad_login,first_name,middle_name,last_name,email,mobile,idle_timeout from public.\"MB001001\" where user_name=?",username);
  if(users.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid login ID or password");
  Map<String,Object> u=users.get(0); long uid=((Number)u.get("user_id")).longValue();
  if(!"ACTIVE".equalsIgnoreCase(Objects.toString(u.get("status"),""))) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"User account is not active");
  int failed=((Number)u.getOrDefault("bad_login",0)).intValue();
  if(failed>=5) throw new ResponseStatusException(HttpStatus.LOCKED,"User account is locked. Contact administrator");
  List<String> hashes=db.query("select password_hash from public.\"MB001002\" where user_id=? order by password_changed_at desc limit 1",(rs,n)->rs.getString(1),uid);
  if(hashes.isEmpty()||!passwords.matches(password,hashes.get(0))){db.update("update public.\"MB001001\" set bad_login=bad_login+1 where user_id=?",uid);throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid login ID or password");}
  db.update("update public.\"MB001001\" set bad_login=0 where user_id=?",uid);
  String role=Objects.toString(u.get("user_role"),Objects.toString(u.get("user_type"),"STAFF")).toUpperCase(Locale.ROOT); if(role.contains("ADMIN")) role="ADMIN"; else if(role.contains("STAFF")) role="STAFF";
  String branch=Objects.toString(u.get("branch"),""); String challenge=UUID.randomUUID().toString(); String name=String.join(" ",List.of(Objects.toString(u.get("first_name"),""),Objects.toString(u.get("middle_name"),""),Objects.toString(u.get("last_name"),"")).stream().filter(x->!x.isBlank()).toList());
  pending.put(challenge,new Pending(uid,username,role,branch,name,Objects.toString(u.get("email"),""),Objects.toString(u.get("mobile"),""),u.get("idle_timeout")==null?null:((Number)u.get("idle_timeout")).intValue(),Instant.now().plusSeconds(otpTtl*60)));
  return Map.of("otpRequired",true,"challengeId",challenge,"message","OTP sent");
 }
 @PostMapping("/verify-otp") public Map<String,Object> verify(@RequestBody Map<String,String> body){String id=body.get("challengeId"); Pending p=pending.remove(id); if(p==null||p.expires().isBefore(Instant.now())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"OTP challenge expired. Login again"); if(!Objects.equals(otpValue,body.get("otp"))) {pending.put(id,p);throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid OTP");}
  String token=jwt.create(p.userId(),p.username(),p.role(),p.branch()); db.update("update public.\"MB001001\" set token=?,login_time=now(),logout_time=null,bad_login=0 where user_id=?",token,p.userId());
  Map<String,Object> user=new LinkedHashMap<>();user.put("id",Long.toString(p.userId()));user.put("name",p.name());user.put("email",p.email());user.put("phone",p.phone());user.put("role",p.role().equals("ADMIN")?"BANK_ADMIN":"BANK_USER");user.put("userRole",p.role());user.put("branch",p.branch());user.put("loginId",p.username());user.put("idleTimeoutMinutes",p.idleTimeout());
  return Map.of("accessToken",token,"user",user);
 }
 @PostMapping("/logout") public Map<String,String> logout(org.springframework.security.core.Authentication auth){if(auth!=null) db.update("update public.\"MB001001\" set token=null,logout_time=now() where user_id=?",Long.parseLong(auth.getName()));return Map.of("message","Logged out");}
 @GetMapping("/profile") public Map<String,Object> profile(org.springframework.security.core.Authentication auth){return db.queryForMap("select user_id as id,user_name as login_id,full_name,user_role,user_type,branch,email,mobile,status from public.\"MB001001\" where user_id=?",Long.parseLong(auth.getName()));}
}
