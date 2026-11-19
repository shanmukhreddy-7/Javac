package com.javac.campus;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javac.campus.auth.EmailDelivery;
import com.javac.campus.main.JavacApplication;
import jakarta.servlet.http.Cookie;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.session.web.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;

/**
 * Tests JDBC-backed sessions through fresh HTTP-style requests using a cookie jar, rather than
 * reusing MockHttpSession directly. Isolated H2 tables exercise the shared-session schema and
 * authentication serialization. This validates database persistence behavior, not a live Neon
 * deployment.
 */
@SpringBootTest(
    classes = {JavacApplication.class, SharedSessionFlowTest.Sessions.class},
    properties = {
      "app.verification-mode=otp",
      "spring.datasource.url=jdbc:h2:mem:sharedsessions;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SharedSessionFlowTest {
  /**
   * Enables JDBC session storage specifically for this test context without enabling the full
   * production profile.
   */
  @TestConfiguration
  @EnableJdbcHttpSession
  static class Sessions {
    /**
     * Test-only cookie configuration retains the production cookie name while allowing the local
     * MockMvc environment to manage it.
     */
    @Bean
    CookieSerializer cookieSerializer() {
      var cookie = new DefaultCookieSerializer();
      cookie.setCookieName("JAVAC_SESSION");
      return cookie;
    }
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @MockitoBean EmailDelivery mail;
  final Map<String, String> cookies = new HashMap<>();

  /**
   * Simulates a small browser: fetches CSRF for writes, sends saved cookies, performs GET/POST,
   * and captures new Set-Cookie values. Each request recovers authentication from JDBC session
   * storage.
   */
  MvcResult request(String path, Object body) throws Exception {
    String token = null;
    if (body != null)
      token =
          json.readTree(request("/api/auth/csrf", null).getResponse().getContentAsString())
              .get("token")
              .asText();
    var request =
        body == null
            ? get(path)
            : post(path)
                .contentType("application/json")
                .content(json.writeValueAsString(body))
                .header("X-CSRF-TOKEN", token);
    if (!cookies.isEmpty())
      request.cookie(
          cookies.entrySet().stream()
              .map(e -> new Cookie(e.getKey(), e.getValue()))
              .toArray(Cookie[]::new));
    var result = mvc.perform(request).andReturn();
    for (String header : result.getResponse().getHeaders("Set-Cookie")) {
      var pair = header.split(";", 2)[0].split("=", 2);
      cookies.put(pair[0], pair.length > 1 ? pair[1] : "");
    }
    return result;
  }

  /**
   * Registers/verifies/logs in a test student, confirms JDBC session rows exist, reads /me using
   * only cookies, logs out, and confirms a later /me returns 401.
   */
  @Test
  void postgresStyleSessionTablesPreserveVerifiedLoginAcrossFreshHttpRequests() throws Exception {
    var code = new AtomicReference<String>();
    doAnswer(
            i -> {
              code.set(i.getArgument(2));
              return null;
            })
        .when(mail)
        .send(anyString(), anyString(), anyString());
    var password = "CampusShared!2026";
    assertThat(
            request(
                    "/api/auth/register",
                    Map.of(
                        "fullName",
                        "Session Student",
                        "email",
                        "session@example.edu",
                        "registrationNumber",
                        "SESSION-2026",
                        "department",
                        "Computing",
                        "studyYear",
                        2,
                        "password",
                        password,
                        "confirmPassword",
                        password,
                        "termsAccepted",
                        true))
                .getResponse()
                .getStatus())
        .isEqualTo(201);
    assertThat(
            request(
                    "/api/auth/verify-otp",
                    Map.of("email", "session@example.edu", "code", code.get()))
                .getResponse()
                .getStatus())
        .isEqualTo(200);
    assertThat(
            request("/api/auth/login", Map.of("email", "session@example.edu", "password", password))
                .getResponse()
                .getStatus())
        .isEqualTo(200);
    assertThat(cookies).containsKey("JAVAC_SESSION");
    assertThat(db.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION", Integer.class))
        .isPositive();
    assertThat(request("/api/auth/me", null).getResponse().getStatus()).isEqualTo(200);
    assertThat(request("/api/auth/logout", Map.of()).getResponse().getStatus()).isEqualTo(200);
    assertThat(request("/api/auth/me", null).getResponse().getStatus()).isEqualTo(401);
  }
}
