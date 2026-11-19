package com.javac.campus.security;

import com.javac.campus.user.UserRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rechecks the stored account on authenticated requests.
 * Suspension, deletion, or a credential-version change invalidates an existing session.
 *
 * A saved session identity can become stale after password changes or suspension. Rechecking
 * current user state on requests makes those changes effective without waiting for cookie
 * expiry.
 */
public class AccountSessionFilter extends OncePerRequestFilter {
  // Reloads current account state so a stale session cannot bypass suspension/password changes.
  private final UserRepository users;

  /**
   * SecurityConfig constructs this filter with the application UserRepository, then installs it
   * after security-context loading so it can validate an already restored session principal.
   */
  public AccountSessionFilter(UserRepository users) {
    this.users = users;
  }

  /**
   * Once per request, compares an authenticated principal against the current user row.
   * Missing/unverified/inactive accounts or a different credentialVersion invalidate the
   * session and clear authentication. The filter continues the chain; authorization then
   * decides whether the resulting anonymous request may proceed.
   */
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof StudentPrincipal p) {
      var user = users.findById(p.id()).orElse(null);
      // Compare against current database state, not only the identity cached at login.
      if (user == null
          || !user.isEmailVerified()
          || !"ACTIVE".equals(user.getAccountStatus())
          || user.getCredentialVersion() != p.credentialVersion()) {
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        SecurityContextHolder.clearContext();
      }
    }
    chain.doFilter(request, response);
  }
}
