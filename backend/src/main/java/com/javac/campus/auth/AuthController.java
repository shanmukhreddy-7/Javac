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

/**
 * HTTP endpoints for registration, email verification, password recovery, and
 * session login.
 * Converts validated request Data transfer objects into service calls and
 * persists the Spring
 * Security context.
 *
 * Read this file as the HTTP layer: @RequestBody decodes JSON, @Valid checks
 * DTO constraints,
 * and @AuthenticationPrincipal reads session identity. No SQL belongs here;
 * AuthService and
 * StudentService perform the account work.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
  // Account business rules; controller delegates
  // registration/verification/recovery here.
  private final AuthService auth;
  // Safe profile lookup used after login and by the current-account endpoint.
  private final StudentService students;
  // Persists Spring Security authentication in the HTTP session across requests.
  private final SecurityContextRepository contexts;

  /**
   * Constructor injection: Spring supplies the collaborators declared here, so
   * this class can
   * delegate work without constructing its own repositories or services.
   */
  public AuthController(
      AuthService auth, StudentService students, SecurityContextRepository contexts) {
    this.auth = auth;
    this.students = students;
    this.contexts = contexts;
  }

  /**
   * GET /api/auth/csrf: resolves the session CSRF token and returns its value and
   * required
   * header name. The client sends that header with later writes; no-store
   * prevents a browser
   * cache from reusing this security response.
   */
  @GetMapping("/csrf")
  public Map<String, String> csrf(CsrfToken token, HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
    return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
  }

  /**
   * GET /api/auth/config: returns public registration settings, not secrets. This
   * response
   * currently advertises OTP verification and disables presentation-mode
   * authentication.
   */
  @GetMapping("/config")
  public Map<String, Object> config() {
    return Map.of(
        "approvedDomains",
        auth.approvedDomains(),
        "verificationRequired",
        true,
        "presentationMode",
        false,
        "verificationMode",
        "otp");
  }

  /**
   * POST /api/auth/register: @Valid checks the Register record before this method
   * runs. The
   * service creates account-related records; HTTP 201 and a safe message confirm
   * creation
   * without returning a verification secret.
   */
  @PostMapping("/register")
  public ResponseEntity<Message> register(@Valid @RequestBody Register r) {
    auth.register(r);
    return ResponseEntity.status(201)
        .body(new Message("Account created. Check your university email to verify it."));
  }

  /**
   * POST /api/auth/login: the service validates credentials and returns a minimal
   * principal.
   * This handler rotates the session ID, saves authentication through the shared
   * context
   * repository, resets CSRF state, and returns a safe profile DTO. Saving the
   * context is what
   * makes later requests remain signed in.
   */
  @PostMapping("/login")
  public ProfileDtos.View login(
      @Valid @RequestBody Login r, HttpServletRequest request, HttpServletResponse response) {
    var principal = auth.login(r);
    request.getSession(true);
    // Rotate the session ID after login so an earlier anonymous session cannot be
    // reused as-is.
    request.changeSessionId();
    var authentication = UsernamePasswordAuthenticationToken.authenticated(
        principal, null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
    // Persist authentication across later requests instead of keeping it only on
    // this thread.
    contexts.saveContext(context, request, response);
    // Discard the old CSRF token; the client fetches a fresh token for its next
    // write.
    new HttpSessionCsrfTokenRepository().saveToken(null, request, response);
    return students.get(principal.id());
  }

  /**
   * GET /api/auth/me: Spring supplies the authenticated StudentPrincipal; its ID
   * selects the
   * current profile. The response is private account data and must not be cached.
   */
  @GetMapping("/me")
  public ProfileDtos.View me(
      @AuthenticationPrincipal StudentPrincipal p, HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
    return students.get(p.id());
  }

  /**
   * POST /api/auth/verify-otp: passes the email and six-digit account code to the
   * service.
   * Success activates the account; it does not log the student in automatically.
   */
  @PostMapping("/verify-otp")
  public Message verifyOtp(@Valid @RequestBody Otp r) {
    auth.verifyOtp(r.email(), r.code());
    return new Message("Email verified. You can now log in.");
  }

  /**
   * POST /api/auth/reset-otp: checks the recovery code through the service and
   * changes the
   * password. The new credential version makes previously issued sessions
   * unusable.
   */
  @PostMapping("/reset-otp")
  public Message resetOtp(@Valid @RequestBody ResetOtp r) {
    auth.resetOtp(r.email(), r.code(), r.password());
    return new Message("Password reset. Please log in.");
  }

  /**
   * POST /api/auth/verify-email: consumes a single-use verification link token.
   * This endpoint
   * supports link mode, separate from the six-digit OTP endpoint.
   */
  @PostMapping("/verify-email")
  public Message verify(@Valid @RequestBody TokenRequest r) {
    auth.verify(r.token());
    return new Message("Your university email is verified. You can now log in.");
  }

  /**
   * Requests another ownership code or link. The deliberately generic reply is
   * the same for an
   * unknown, already verified, or cooling-down account, so it does not disclose
   * registration
   * state.
   */
  @PostMapping("/resend-verification")
  public Message resend(@Valid @RequestBody EmailRequest r) {
    auth.resend(r.email());
    return new Message(
        "If this account needs verification, a code will be sent. Allow 60 seconds between"
            + " requests.");
  }

  /**
   * Starts recovery for an eligible active account. A generic response avoids
   * revealing whether
   * the supplied email is registered; delivery is handled inside the service.
   */
  @PostMapping("/forgot-password")
  public Message forgot(@Valid @RequestBody EmailRequest r) {
    auth.forgotPassword(r.email());
    return new Message(
        "If an eligible account exists, a reset code will be sent. Allow 60 seconds between"
            + " requests.");
  }

  /**
   * Completes link-based recovery using a valid, unused, unexpired token and a
   * validated new
   * password. This is the link-mode counterpart of resetOtp.
   */
  @PostMapping("/reset-password")
  public Message reset(@Valid @RequestBody Reset r) {
    auth.resetPassword(r);
    return new Message("Password reset. Please log in with your new password.");
  }

  /**
   * Changes the signed-in account password after checking its current password.
   * Invalidates
   * this HTTP session and clears this request thread's security context,
   * requiring a fresh
   * login.
   */
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
