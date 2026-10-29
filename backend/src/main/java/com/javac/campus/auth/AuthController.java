package com.javac.campus.auth;

import com.javac.campus.auth.AuthDtos.*;
import com.javac.campus.profile.*;
import com.javac.campus.security.StudentPrincipal;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService auth;
  private final StudentService students;
  private final SecurityContextRepository contexts;

  public AuthController(
      AuthService auth, StudentService students, SecurityContextRepository contexts) {
    this.auth = auth;
    this.students = students;
    this.contexts = contexts;
  }

  @GetMapping("/csrf")
  public Map<String, String> csrf(CsrfToken token, HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
    return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
  }

  @GetMapping("/config")
  public Map<String, Object> config() {
    return Map.of("approvedDomains", auth.approvedDomains());
  }

  @PostMapping("/register")
  public ResponseEntity<Message> register(@Valid @RequestBody Register r) {
    auth.register(r);
    return ResponseEntity.status(201)
        .body(new Message("Account created. Check your university email to verify it."));
  }

  @PostMapping("/login")
  public ProfileDtos.View login(
      @Valid @RequestBody Login r, HttpServletRequest request, HttpServletResponse response) {
    var principal = auth.login(r);
    request.getSession(true);
    request.changeSessionId();
    var authentication =
        UsernamePasswordAuthenticationToken.authenticated(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
    contexts.saveContext(context, request, response);
    new HttpSessionCsrfTokenRepository().saveToken(null, request, response);
    return students.get(principal.id());
  }

  @GetMapping("/me")
  public ProfileDtos.View me(
      @AuthenticationPrincipal StudentPrincipal p, HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
    return students.get(p.id());
  }

  @PostMapping("/verify-email")
  public Message verify(@Valid @RequestBody TokenRequest r) {
    auth.verify(r.token());
    return new Message("Your university email is verified. You can now log in.");
  }

  @PostMapping("/resend-verification")
  public Message resend(@Valid @RequestBody EmailRequest r) {
    auth.resend(r.email());
    return new Message(
        "If this account needs verification, a link will be sent. Allow 60 seconds between"
            + " requests.");
  }

  @PostMapping("/forgot-password")
  public Message forgot(@Valid @RequestBody EmailRequest r) {
    auth.forgotPassword(r.email());
    return new Message(
        "If an eligible account exists, a reset link will be sent. Allow 60 seconds between"
            + " requests.");
  }

  @PostMapping("/reset-password")
  public Message reset(@Valid @RequestBody Reset r) {
    auth.resetPassword(r);
    return new Message("Password reset. Please log in with your new password.");
  }

  @PostMapping("/change-password")
  public Message change(
      @AuthenticationPrincipal StudentPrincipal p,
      @Valid @RequestBody Change r,
      HttpServletRequest request) {
    auth.changePassword(p.id(), r);
    request.getSession().invalidate();
    SecurityContextHolder.clearContext();
    return new Message("Password changed. Please log in again.");
  }
}
