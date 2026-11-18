package com.abcbank.portal.controller;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
@RequestMapping("/auth")
public class SessionVerificationController {
  private final JdbcTemplate db; private final PasswordEncoder encoder;
  public SessionVerificationController(JdbcTemplate db, PasswordEncoder encoder){this.db=db;this.encoder=encoder;}
  @PostMapping("/verify-session")
  public Map<String,String> verify(@RequestBody Map<String,String> body, Authentication auth){
    if(auth==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Session is not authenticated.");
    long currentId; try { currentId=Long.parseLong(auth.getName()); } catch(Exception e){ throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid authenticated session."); }
    String loginId=Objects.toString(body.get("loginId"),"").trim(), password=Objects.toString(body.get("password"),"");
    if(loginId.isBlank()||password.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Login ID and password are required.");
    List<String> names=db.query("select user_name from public.\"MB001001\" where user_id=? and status='ACTIVE'",(rs,n)->rs.getString(1),currentId);
    if(names.isEmpty()||!names.get(0).equalsIgnoreCase(loginId)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Login ID does not match the current session user.");
    List<String> hashes=db.query("select password_hash from public.\"MB001002\" where user_id=? order by password_changed_at desc limit 1",(rs,n)->rs.getString(1),currentId);
    if(hashes.isEmpty()||!encoder.matches(password,hashes.get(0))) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Incorrect password.");
    return Map.of("verified","true","message","Session verified.");
  }
}
