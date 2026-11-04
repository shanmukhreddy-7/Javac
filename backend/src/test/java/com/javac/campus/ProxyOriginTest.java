package com.javac.campus;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javac.campus.main.JavacApplication;
import java.net.*;
import java.net.http.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/** Exercise real servlet proxy handling: browser POSTs include Origin, unlike basic curl checks. */
@SpringBootTest(
    classes = JavacApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "server.forward-headers-strategy=framework",
      "spring.datasource.url=jdbc:h2:mem:proxyorigin;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"
    })
@ActiveProfiles("test")
class ProxyOriginTest {
  @LocalServerPort int port;
  @Autowired ObjectMapper json;
  final String preview = "https://javac-preview.vercel.app";

  HttpRequest.Builder request(String path) {
    return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
        .header("X-Forwarded-Proto", "https")
        .header("X-Forwarded-Host", "javac-preview.vercel.app")
        .header("X-Forwarded-Port", "443");
  }

  @Test
  void sameOriginBrowserLoginBehindHttpsProxyReachesAuthentication() throws Exception {
    var client =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .build();
    var csrf =
        client.send(request("/api/auth/csrf").GET().build(), HttpResponse.BodyHandlers.ofString());
    assertThat(csrf.statusCode()).isEqualTo(200);
    var token = json.readTree(csrf.body()).get("token").asText();
    var response =
        client.send(
            request("/api/auth/login")
                .header("Origin", preview)
                .header("Content-Type", "application/json")
                .header("X-CSRF-TOKEN", token)
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        json.writeValueAsString(
                            Map.of(
                                "email",
                                "nonexistent@example.edu",
                                "password",
                                "InvalidCheck!2026"))))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.headers().firstValue("content-type").orElse(""))
        .contains("application/json");
  }

  @Test
  void unrelatedBrowserOriginIsStillRejected() throws Exception {
    var response =
        HttpClient.newHttpClient()
            .send(
                request("/api/auth/login")
                    .header("Origin", "https://untrusted.example")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build(),
                HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(response.body()).contains("Invalid CORS request");
  }
}
