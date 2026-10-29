package com.javac.campus.security;

import com.javac.campus.user.UserRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class AccountSessionFilter extends OncePerRequestFilter {
  private final UserRepository users;

  public AccountSessionFilter(UserRepository users) {
    this.users = users;
  }

  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof StudentPrincipal p) {
      var user = users.findById(p.id()).orElse(null);
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
