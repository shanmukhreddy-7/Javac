package com.javac.campus.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounded, single-instance limiter. Remote address is used; forwarded headers are not trusted. */
public class AuthRateLimitFilter extends OncePerRequestFilter {
  private record Window(long start, int count) {}

  private final Map<String, Window> windows = new HashMap<>();
  private final int limit;

  public AuthRateLimitFilter(int limit) {
    this.limit = limit;
  }

  private synchronized boolean allow(String key) {
    long now = System.currentTimeMillis();
    windows.entrySet().removeIf(e -> now - e.getValue().start() > 60000);
    var previous = windows.get(key);
    if (previous == null) {
      if (windows.size() >= 10000) return false;
      windows.put(key, new Window(now, 1));
      return true;
    }
    if (previous.count() >= limit) return false;
    windows.put(key, new Window(previous.start(), previous.count() + 1));
    return true;
  }

  protected void doFilterInternal(HttpServletRequest r, HttpServletResponse s, FilterChain c)
      throws ServletException, IOException {
    if ("POST".equals(r.getMethod())
        && r.getServletPath().startsWith("/api/auth/")
        && !allow(r.getRemoteAddr())) {
      s.setStatus(429);
      s.setHeader("Retry-After", "60");
      s.setContentType("application/json");
      s.getWriter()
          .write("{\"message\":\"Too many attempts. Please wait one minute.\",\"fields\":{}}");
      return;
    }
    c.doFilter(r, s);
  }
}
