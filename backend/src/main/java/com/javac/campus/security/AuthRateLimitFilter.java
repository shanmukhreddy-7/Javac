package com.javac.campus.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounded, single-instance limiter. Remote address is used; forwarded headers are not trusted.
 *
 * OncePerRequestFilter participates in the servlet chain. Window is an immutable snapshot of
 * start time and count; a synchronized method protects the mutable map. This is a bounded
 * single-instance limiter, not a shared database-backed quota.
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {
  /**
   * Immutable per-address window: start is epoch milliseconds and count is the number of
   * admitted requests in that window.
   */
  private record Window(long start, int count) {}

  // Mutable per-remote-address state; allow synchronizes access and expires old entries.
  private final Map<String, Window> windows = new HashMap<>();
  // Maximum admitted auth POSTs per 60-second window on this Java instance.
  private final int limit;

  /**
   * SecurityConfig constructs this filter with the configured per-address request limit and
   * installs it in the security chain. Its counter map starts empty for this Java instance.
   */
  public AuthRateLimitFilter(int limit) {
    this.limit = limit;
  }

  /**
   * Synchronizes access to the local window map so simultaneous requests cannot lose
   * increments. Removes windows older than 60 seconds, bounds tracked addresses to 10,000, and
   * counts requests against the configured per-address limit. State is per JVM, not a
   * distributed cluster-wide limit.
   */
  private synchronized boolean allow(String key) {
    long now = System.currentTimeMillis();
    // Remove expired windows before checking the bounded map to avoid unbounded address retention.
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

  /**
   * Applies the limiter to POST requests under /api/auth/ using the servlet remote address. An
   * exhausted address receives HTTP 429 and Retry-After: 60; other requests continue normally.
   * Forwarded headers are not read directly by this limiter.
   */
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
