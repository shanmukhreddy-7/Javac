package com.javac.campus.config;

import org.springframework.context.annotation.*;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;

/**
 * Production session configuration backed by shared JDBC storage with secure HTTP-only cookies.
 *
 * Only prod enables this configuration. SPRING_SESSION stores session metadata and
 * SPRING_SESSION_ATTRIBUTES stores serialized values such as the security context; the browser
 * carries a session identifier rather than account credentials.
 */
@Configuration
@Profile("prod")
@EnableJdbcHttpSession(maxInactiveIntervalInSeconds = 1800)
public class SharedSessionConfig {
  /**
   * Configures the browser session cookie name and security flags. Secure limits transmission
   * to HTTPS, HttpOnly blocks script access, and SameSite=Lax limits cross-site cookie sending.
   * The class-level JDBC-session annotation sets a 1,800-second inactivity limit.
   */
  @Bean
  org.springframework.session.web.http.CookieSerializer cookieSerializer() {
    var cookie = new org.springframework.session.web.http.DefaultCookieSerializer();
    cookie.setCookieName("JAVAC_SESSION");
    cookie.setUseSecureCookie(true);
    cookie.setUseHttpOnlyCookie(true);
    cookie.setSameSite("Lax");
    return cookie;
  }
}
