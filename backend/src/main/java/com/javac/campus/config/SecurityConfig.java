package com.javac.campus.config;

import com.javac.campus.security.*;
import com.javac.campus.user.UserRepository;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.web.*;
import org.springframework.security.web.context.*;
import org.springframework.web.cors.*;

/**
 * Defines password hashing, session authentication, CORS, CSRF-protected request handling, and filters.
 * PBKDF2 is the current password encoder; no browser-side check replaces these server protections.
 *
 * Spring calls @Bean methods to register shared objects. Authentication answers who the caller
 * is; service participant/admin checks answer which job or operation that caller may access.
 * CORS origin policy and CSRF tokens solve different browser-request concerns.
 */
@Configuration
public class SecurityConfig {
  /**
   * Registers the application-wide PBKDF2 encoder with Spring Security's v5.8 defaults.
   * Services encode new passwords/codes and use matches for checking them; they never decrypt a
   * saved password.
   */
  @Bean
  PasswordEncoder passwordEncoder() {
    return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
  }

  /**
   * Stores Spring's authenticated security context in the HTTP session. Production
   * SharedSessionConfig backs sessions with JDBC so later requests can recover the same
   * principal.
   */
  @Bean
  SecurityContextRepository contextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  /**
   * Builds the servlet security filter chain: explicit CORS origins, saved session context,
   * public UI/auth paths, authenticated remaining APIs, safe JSON failures, and logout. Default
   * Spring CSRF protection remains enabled; custom rate-limit and account-state filters are
   * positioned around context loading and CSRF handling.
   */
  @Bean
  SecurityFilterChain security(
      HttpSecurity http,
      UserRepository users,
      SecurityContextRepository repository,
      @Value("${app.allowed-origins}") String origins,
      @Value("${app.rate-limit}") int limit)
      throws Exception {
    var cors = new CorsConfiguration();
    cors.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).toList());
    cors.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "OPTIONS"));
    cors.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN"));
    // Cookie-based authentication requires explicit allowed origins rather than a wildcard.
    cors.setAllowCredentials(true);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", cors);
    http.cors(c -> c.configurationSource(source))
        .securityContext(c -> c.securityContextRepository(repository))
        .authorizeHttpRequests(
            a ->
                // These public paths load the React shell. Data behind authenticated API routes
                // remains protected.
                a.requestMatchers(
                        "/",
                        "/index.html",
                        "/assets/**",
                        "/fonts/**",
                        "/javac-logo.png",
                        "/favicon.png",
                        "/presentation",
                        "/presentation/**",
                        "/login",
                        "/register",
                        "/verify-email",
                        "/forgot-password",
                        "/reset-password",
                        "/dashboard",
                        "/profile",
                        "/settings",
                        "/requests",
                        "/jobs",
                        "/jobs/**",
                        "/my-jobs",
                        "/earnings",
                        "/notifications",
                        "/admin",
                        "/students/**")
                    .permitAll()
                    .requestMatchers(
                        "/api/auth/csrf",
                        "/api/auth/config",
                        "/api/skills",
                        "/api/auth/register",
                        "/api/auth/login",
                        "/api/auth/verify-email",
                        "/api/auth/verify-otp",
                        "/api/auth/reset-otp",
                        "/api/auth/resend-verification",
                        "/api/auth/forgot-password",
                        "/api/auth/reset-password")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (r, s, x) -> {
                          s.setStatus(401);
                          s.setContentType("application/json");
                          s.getWriter()
                              .write("{\"message\":\"Please log in to continue.\",\"fields\":{}}");
                        })
                    .accessDeniedHandler(
                        (r, s, x) -> {
                          s.setStatus(403);
                          s.setContentType("application/json");
                          s.getWriter()
                              .write(
                                  "{\"message\":\"Session or security token expired. Refresh and"
                                      + " try again.\",\"fields\":{}}");
                        }))
        .logout(
            l ->
                l.logoutUrl("/api/auth/logout")
                    .invalidateHttpSession(true)
                    .deleteCookies("JAVAC_SESSION")
                    .logoutSuccessHandler(
                        (r, s, a) -> {
                          s.setStatus(200);
                          s.setContentType("application/json");
                          s.getWriter().write("{\"message\":\"You have been logged out.\"}");
                        }))
        // Throttle authentication requests before CSRF handling, then recheck account state after
        // context loading.
        .addFilterBefore(
            new AuthRateLimitFilter(limit), org.springframework.security.web.csrf.CsrfFilter.class)
        .addFilterAfter(new AccountSessionFilter(users), SecurityContextHolderFilter.class);
    return http.build();
  }
}
