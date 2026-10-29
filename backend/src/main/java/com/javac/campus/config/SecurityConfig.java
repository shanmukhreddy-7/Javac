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

@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
  }

  @Bean
  SecurityContextRepository contextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

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
    cors.setAllowCredentials(true);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", cors);
    http.cors(c -> c.configurationSource(source))
        .securityContext(c -> c.securityContextRepository(repository))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/api/auth/csrf",
                        "/api/auth/config",
                        "/api/skills",
                        "/api/auth/register",
                        "/api/auth/login",
                        "/api/auth/verify-email",
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
        .addFilterBefore(
            new AuthRateLimitFilter(limit), org.springframework.security.web.csrf.CsrfFilter.class)
        .addFilterAfter(new AccountSessionFilter(users), SecurityContextHolderFilter.class);
    return http.build();
  }
}
