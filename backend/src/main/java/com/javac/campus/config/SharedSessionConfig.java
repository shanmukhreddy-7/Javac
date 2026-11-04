package com.javac.campus.config;

import org.springframework.context.annotation.*;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;

@Configuration
@Profile("prod")
@EnableJdbcHttpSession(maxInactiveIntervalInSeconds = 1800)
public class SharedSessionConfig {
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
