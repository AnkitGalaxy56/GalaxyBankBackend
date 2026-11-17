package com.abcbank.portal.controller;

import jakarta.annotation.PostConstruct;

import com.abcbank.portal.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;

@RestController @RequestMapping("/auth")
public class AuthController {
 private final JdbcTemplate db; private final PasswordEncoder passwords; private final JwtService jwt;
 @Value("${portal.otp.value}") private String otpValue;
 @Value("${portal.otp.ttl-minutes:5}") private long otpTtl;
 @Value("${portal.jwt.refresh-ttl-hours:8}") private long refreshTtlHours;
 private static final SecureRandom REFRESH_RANDOM = new SecureRandom();
 private record Pending(long userId,String username,String role,String branch,String name,String email,String phone,Integer idleTimeout,Instant expires){}
 private final Map<String,Pending> pending=new ConcurrentHashMap<>();
 private record PasswordChange(String encodedPassword, Instant expires) {}
 private final Map<Long,PasswordChange> passwordChanges=new ConcurrentHashMap<>();
 public AuthController(JdbcTemplate db,PasswordEncoder passwords,JwtService jwt){this.db=db;this.passwords=passwords;this.jwt=jwt;}
 @PostConstruct void initRefreshTokens(){
  db.execute("create table if not exists public.portal_auth_refresh_tokens (id bigserial primary key, user_id bigint not null, token_hash varchar(64) not null unique, expires_at timestamptz not null, created_at timestamptz not null default now(), revoked_at timestamptz)");
  db.execute("create index if not exists idx_portal_auth_refresh_user on public.portal_auth_refresh_tokens(user_id)");
 }
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
  String userType=Objects.toString(u.get("user_type"),"STAFF").toUpperCase(Locale.ROOT); String role="ADMIN".equals(userType)?"ADMIN":"STAFF";
  String branch=Objects.toString(u.get("branch"),""); String challenge=UUID.randomUUID().toString(); String name=String.join(" ",List.of(Objects.toString(u.get("first_name"),""),Objects.toString(u.get("middle_name"),""),Objects.toString(u.get("last_name"),"")).stream().filter(x->!x.isBlank()).toList());
  pending.put(challenge,new Pending(uid,username,role,branch,name,Objects.toString(u.get("email"),""),Objects.toString(u.get("mobile"),""),u.get("idle_timeout")==null?null:((Number)u.get("idle_timeout")).intValue(),Instant.now().plusSeconds(otpTtl*60)));
  return Map.of("otpRequired",true,"challengeId",challenge,"message","OTP sent");
 }
 @PostMapping("/verify-otp") public Map<String,Object> verify(@RequestBody Map<String,String> body){String id=body.get("challengeId"); Pending p=pending.remove(id); if(p==null||p.expires().isBefore(Instant.now())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"OTP challenge expired. Login again"); if(!Objects.equals(otpValue,body.get("otp"))) {pending.put(id,p);throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid OTP");}
  String token=jwt.create(p.userId(),p.username(),p.role(),p.branch());
  String refreshToken=newRefreshToken();
  db.update("insert into public.portal_auth_refresh_tokens(user_id,token_hash,expires_at) values(?,?,?)",p.userId(),hashRefreshToken(refreshToken),Timestamp.from(Instant.now().plusSeconds(Math.max(1,refreshTtlHours)*3600)));
  db.update("update public.\"MB001001\" set token=?,login_time=now(),logout_time=null,bad_login=0 where user_id=?",token,p.userId());
  Map<String,Object> user=new LinkedHashMap<>();user.put("id",Long.toString(p.userId()));user.put("name",p.name());user.put("email",p.email());user.put("phone",p.phone());user.put("role",p.role().equals("ADMIN")?"BANK_ADMIN":"BANK_USER");user.put("userRole",p.role());user.put("branch",p.branch());user.put("loginId",p.username());user.put("idleTimeoutMinutes",p.idleTimeout());
  return Map.of("accessToken",token,"refreshToken",refreshToken,"user",user);
 }
 @PostMapping("/refresh") @Transactional public Map<String,String> refresh(@RequestBody Map<String,String> body){
  String supplied=Objects.toString(body.get("refreshToken"),"").trim();
  if(supplied.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Refresh token is required.");
  String oldHash=hashRefreshToken(supplied);
  List<Long> ids=db.query("select user_id from public.portal_auth_refresh_tokens where token_hash=? and revoked_at is null and expires_at>now() for update",(rs,n)->rs.getLong(1),oldHash);
  if(ids.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Refresh session expired. Please sign in again.");
  long uid=ids.get(0);
  Map<String,Object> u;
  try { u=db.queryForMap("select user_name,user_type,branch,status from public.\"MB001001\" where user_id=?",uid); }
  catch(Exception e){ throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"User session is no longer available."); }
  if(!"ACTIVE".equalsIgnoreCase(Objects.toString(u.get("status"),""))) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"User account is not active.");
  String role="ADMIN".equalsIgnoreCase(Objects.toString(u.get("user_type"),""))?"ADMIN":"STAFF";
  String username=Objects.toString(u.get("user_name"),"");
  String branch=Objects.toString(u.get("branch"),"");
  String accessToken=jwt.create(uid,username,role,branch);
  String nextRefreshToken=newRefreshToken();
  int revoked=db.update("update public.portal_auth_refresh_tokens set revoked_at=now() where token_hash=? and revoked_at is null",oldHash);
  if(revoked!=1) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Refresh session was already used.");
  db.update("insert into public.portal_auth_refresh_tokens(user_id,token_hash,expires_at) values(?,?,?)",uid,hashRefreshToken(nextRefreshToken),Timestamp.from(Instant.now().plusSeconds(Math.max(1,refreshTtlHours)*3600)));
  db.update("update public.\"MB001001\" set token=? where user_id=?",accessToken,uid);
  return Map.of("accessToken",accessToken,"refreshToken",nextRefreshToken);
 }
 @PostMapping("/logout") public Map<String,String> logout(org.springframework.security.core.Authentication auth){if(auth!=null){long uid=Long.parseLong(auth.getName());db.update("update public.\"MB001001\" set token=null,logout_time=now() where user_id=?",uid);db.update("update public.portal_auth_refresh_tokens set revoked_at=now() where user_id=? and revoked_at is null",uid);}return Map.of("message","Logged out");}
 @GetMapping("/profile") public Map<String,Object> profile(org.springframework.security.core.Authentication auth){
  Map<String,Object> r=db.queryForMap("select user_id,user_name,full_name,first_name,middle_name,last_name,emp_no,designation,dob,user_role,user_type,branch,email,mobile,status,multi_branch,idle_timeout,login_time,logout_time from public.\"MB001001\" where user_id=?",Long.parseLong(auth.getName()));
  Map<String,Object> out=new LinkedHashMap<>();
  out.put("id",Objects.toString(r.get("user_id"))); out.put("fullName",Objects.toString(r.get("full_name"),"")); out.put("firstName",Objects.toString(r.get("first_name"),"")); out.put("middleName",Objects.toString(r.get("middle_name"),"")); out.put("lastName",Objects.toString(r.get("last_name"),""));
  out.put("employeeId",Objects.toString(r.get("emp_no"),"")); out.put("userName",Objects.toString(r.get("user_name"),"")); out.put("email",Objects.toString(r.get("email"),"")); out.put("mobile",Objects.toString(r.get("mobile"),""));
  out.put("dateOfBirth",r.get("dob")==null?"":r.get("dob").toString()); out.put("gender",""); out.put("accountState",Objects.toString(r.get("status"),"")); out.put("designation",Objects.toString(r.get("designation"),""));
  out.put("userRole","ADMIN".equalsIgnoreCase(Objects.toString(r.get("user_role"),""))?"BANK_ADMIN":"BANK_USER"); out.put("loginBranch",Objects.toString(r.get("branch"),"")); out.put("branchId",Objects.toString(r.get("branch"),"")); out.put("allowMultiBranch",Boolean.TRUE.equals(r.get("multi_branch")));
  out.put("allowLoginOnHolidays",false); out.put("permittedLoginTime",""); out.put("permittedLogoutTime","");
  out.put("lastLoginDate",datePart(r.get("login_time"))); out.put("lastLoginTime",timePart(r.get("login_time")));
  out.put("sessionTimeoutSeconds",r.get("idle_timeout")==null?1800:((Number)r.get("idle_timeout")).intValue()); out.put("organizationName","Allianza"); out.put("organizationCode","ALLIANZA");
  return out;
 }
 @PutMapping("/profile") public Map<String,Object> updateProfile(@RequestBody Map<String,String> body,org.springframework.security.core.Authentication auth){
  String email=Objects.toString(body.get("email"),"").trim(), mobile=Objects.toString(body.get("mobile"),"").trim();
  if(!email.matches("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a valid email address.");
  if(!mobile.matches("^[6-9][0-9]{9}$")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a valid 10-digit mobile number.");
  db.update("update public.\"MB001001\" set email=?,mobile=?,modified_by=?,modified_on=now() where user_id=?",email,mobile,Long.parseLong(auth.getName()),Long.parseLong(auth.getName()));
  return profile(auth);
 }
 @PostMapping("/change-password/request-otp") public Map<String,String> requestPasswordChange(@RequestBody Map<String,String> body,org.springframework.security.core.Authentication auth){
  long uid=Long.parseLong(auth.getName()); String oldPassword=body.get("oldPassword"), newPassword=body.get("newPassword");
  if(newPassword==null||newPassword.length()<8||!newPassword.matches(".*[A-Z].*")||!newPassword.matches(".*[a-z].*")||!newPassword.matches(".*[0-9].*")||!newPassword.matches(".*[^A-Za-z0-9].*")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"New password must contain at least 8 characters, uppercase, lowercase, a number and a special character.");
  List<String> hashes=db.query("select password_hash from public.\"MB001002\" where user_id=? order by password_changed_at desc limit 1",(rs,n)->rs.getString(1),uid);
  if(hashes.isEmpty()||oldPassword==null||!passwords.matches(oldPassword,hashes.get(0))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Current password is incorrect.");
  if(passwords.matches(newPassword,hashes.get(0))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"New password must be different from the current password.");
  passwordChanges.put(uid,new PasswordChange(passwords.encode(newPassword),Instant.now().plusSeconds(otpTtl*60)));
  return Map.of("message","OTP verification required.");
 }
 @PostMapping("/change-password/verify-otp") @Transactional public Map<String,String> verifyPasswordChange(@RequestBody Map<String,String> body,org.springframework.security.core.Authentication auth){
  long uid=Long.parseLong(auth.getName()); PasswordChange change=passwordChanges.get(uid);
  if(change==null||change.expires().isBefore(Instant.now())){passwordChanges.remove(uid);throw new ResponseStatusException(HttpStatus.GONE,"Password change request expired. Start again.");}
  if(!Objects.equals(otpValue,body.get("otp"))) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid OTP.");
  db.update("insert into public.\"MB001002\" (user_id,password_hash,must_reset,created_by) values (?,?,false,?)",uid,change.encodedPassword(),uid);
  db.update("update public.\"MB001001\" set bad_login=0,modified_by=?,modified_on=now() where user_id=?",uid,uid);
  passwordChanges.remove(uid); return Map.of("message","Password changed successfully.");
 }
 private static String newRefreshToken(){byte[] bytes=new byte[48];REFRESH_RANDOM.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
 private static String hashRefreshToken(String token){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("Unable to hash refresh token",e);}}
 private static String datePart(Object value){if(value instanceof Timestamp t)return t.toLocalDateTime().toLocalDate().toString();return "";}
 private static String timePart(Object value){if(value instanceof Timestamp t)return t.toLocalDateTime().toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss"));return "";}
}
