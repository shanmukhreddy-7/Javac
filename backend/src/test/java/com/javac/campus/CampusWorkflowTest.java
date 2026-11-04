package com.javac.campus;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.javac.campus.auth.EmailDelivery;
import com.javac.campus.main.JavacApplication;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;

@SpringBootTest(
    classes = JavacApplication.class,
    properties = {
      "app.verification-mode=otp",
      "app.admin-email=admin@example.edu",
      "spring.datasource.url=jdbc:h2:mem:workflows;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CampusWorkflowTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @MockitoBean EmailDelivery mail;
  final Map<String, String> codes = new ConcurrentHashMap<>();
  final String password = "CampusStrong!2026";

  record Account(long id, MockHttpSession session) {}

  @BeforeEach
  void prepare() {
    for (String table :
        List.of(
            "job_reviews",
            "job_reports",
            "job_messages",
            "notifications",
            "campus_jobs",
            "email_otps",
            "email_verification_tokens",
            "password_reset_tokens",
            "student_skills",
            "student_profiles",
            "users")) db.update("DELETE FROM " + table);
    codes.clear();
    doAnswer(
            i -> {
              codes.put(i.getArgument(0), i.getArgument(2));
              return null;
            })
        .when(mail)
        .send(anyString(), anyString(), anyString());
  }

  ResultActions send(String path, Object body, MockHttpSession session) throws Exception {
    var request =
        post(path)
            .with(csrf())
            .contentType("application/json")
            .content(json.writeValueAsString(body));
    if (session != null) request.session(session);
    return mvc.perform(request);
  }

  JsonNode data(ResultActions a) throws Exception {
    return json.readTree(a.andReturn().getResponse().getContentAsString());
  }

  void register(String name) throws Exception {
    send(
            "/api/auth/register",
            Map.of(
                "fullName",
                name + " Student",
                "email",
                name + "@example.edu",
                "registrationNumber",
                name + "-2026",
                "department",
                "Computing",
                "studyYear",
                2,
                "password",
                password,
                "confirmPassword",
                password,
                "termsAccepted",
                true),
            null)
        .andExpect(status().isCreated());
  }

  Account account(String name) throws Exception {
    register(name);
    send(
            "/api/auth/verify-otp",
            Map.of("email", name + "@example.edu", "code", codes.get(name + "@example.edu")),
            null)
        .andExpect(status().isOk());
    var login =
        send("/api/auth/login", Map.of("email", name + "@example.edu", "password", password), null)
            .andExpect(status().isOk())
            .andReturn();
    return new Account(
        json.readTree(login.getResponse().getContentAsString()).get("id").asLong(),
        (MockHttpSession) login.getRequest().getSession(false));
  }

  long job(Account a) throws Exception {
    return data(send(
                "/api/jobs",
                Map.of(
                    "title",
                    "Collect lunch",
                    "description",
                    "Bring the food bag from Gate 1 to our academic block.",
                    "category",
                    "Food pickup",
                    "reward",
                    30,
                    "pickup",
                    "Gate 1",
                    "destination",
                    "Academic Block",
                    "deadline",
                    Instant.now().plusSeconds(3600).toString()),
                a.session())
            .andExpect(status().isCreated()))
        .get("id")
        .asLong();
  }

  void act(Account a, long id, String action, int expected) throws Exception {
    send("/api/jobs/" + id + "/actions", Map.of("action", action), a.session())
        .andExpect(status().is(expected));
  }

  @Test
  void otpIsHashedAndAttemptLimitPersists() throws Exception {
    register("alex");
    String code = codes.get("alex@example.edu");
    assertThat(code).matches("[0-9]{6}");
    assertThat(
            db.queryForObject(
                "SELECT code_hash FROM email_otps WHERE email='alex@example.edu'", String.class))
        .doesNotContain(code);
    String wrong = code.equals("000000") ? "999999" : "000000";
    for (int i = 0; i < 5; i++)
      send("/api/auth/verify-otp", Map.of("email", "alex@example.edu", "code", wrong), null)
          .andExpect(status().isBadRequest());
    assertThat(
            db.queryForObject(
                "SELECT attempts FROM email_otps WHERE email='alex@example.edu'", Integer.class))
        .isEqualTo(5);
    send("/api/auth/verify-otp", Map.of("email", "alex@example.edu", "code", code), null)
        .andExpect(status().isBadRequest());
  }

  @Test
  void codesExpireAndResendsHaveCooldown() throws Exception {
    register("alex");
    String old = codes.get("alex@example.edu");
    send("/api/auth/resend-verification", Map.of("email", "alex@example.edu"), null)
        .andExpect(status().isOk());
    verify(mail, times(1)).send(eq("alex@example.edu"), anyString(), anyString());
    db.update("UPDATE email_otps SET expires_at=?", Instant.now().minusSeconds(1).toString());
    send("/api/auth/verify-otp", Map.of("email", "alex@example.edu", "code", old), null)
        .andExpect(status().isBadRequest());
  }

  @Test
  void completeJobMessagesReviewsAndOfflineEarnings() throws Exception {
    var owner = account("owner");
    var helper = account("helper");
    var stranger = account("stranger");
    long id = job(owner);
    act(owner, id, "accept", 403);
    act(helper, id, "accept", 200);
    act(stranger, id, "accept", 409);
    mvc.perform(get("/api/jobs/" + id + "/messages").session(stranger.session()))
        .andExpect(status().isForbidden());
    send("/api/jobs/" + id + "/messages", Map.of("body", "On my way!"), helper.session())
        .andExpect(status().isOk());
    assertThat(
            data(mvc.perform(get("/api/jobs/" + id + "/messages").session(owner.session()))).size())
        .isEqualTo(1);
    act(owner, id, "start", 409);
    act(helper, id, "start", 200);
    act(helper, id, "complete", 409);
    act(helper, id, "deliver", 200);
    act(owner, id, "complete", 200);
    var earned = data(mvc.perform(get("/api/earnings").session(helper.session())));
    assertThat(earned.get("pending").decimalValue()).isEqualByComparingTo("30");
    send("/api/jobs/" + id + "/collection", Map.of("method", "PHONEPE"), owner.session())
        .andExpect(status().isConflict());
    send("/api/jobs/" + id + "/collection", Map.of("method", "PHONEPE"), helper.session())
        .andExpect(status().isOk());
    send("/api/jobs/" + id + "/collection", Map.of("method", "PHONEPE"), helper.session())
        .andExpect(status().isConflict());
    assertThat(
            data(mvc.perform(get("/api/earnings").session(helper.session())))
                .get("collected")
                .decimalValue())
        .isEqualByComparingTo("30");
    send(
            "/api/jobs/" + id + "/reviews",
            Map.of("rating", 5, "comment", "Very helpful"),
            owner.session())
        .andExpect(status().isOk());
    send(
            "/api/jobs/" + id + "/reviews",
            Map.of("rating", 1, "comment", "Duplicate"),
            owner.session())
        .andExpect(status().isConflict());
    send("/api/jobs/" + id + "/messages", Map.of("body", "Closed"), helper.session())
        .andExpect(status().isConflict());
  }

  @Test
  void adminModeratesAndSuspensionRevokesSession() throws Exception {
    var owner = account("owner");
    var admin = account("admin");
    long id = job(owner);
    mvc.perform(get("/api/admin").session(owner.session())).andExpect(status().isForbidden());
    mvc.perform(get("/api/admin").session(admin.session())).andExpect(status().isOk());
    send("/api/jobs/" + id + "/reports", Map.of("reason", "Please review"), owner.session())
        .andExpect(status().isOk());
    send("/api/admin/jobs/" + id + "/hide", Map.of(), admin.session()).andExpect(status().isOk());
    assertThat(data(mvc.perform(get("/api/jobs").session(owner.session()))).size()).isZero();
    mvc.perform(
            patch("/api/admin/users/" + owner.id())
                .session(admin.session())
                .with(csrf())
                .contentType("application/json")
                .content("{\"status\":\"SUSPENDED\"}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/auth/me").session(owner.session())).andExpect(status().isUnauthorized());
  }

  @Test
  void resetRevokesExistingSessions() throws Exception {
    var alex = account("alex");
    send("/api/auth/forgot-password", Map.of("email", "alex@example.edu"), null)
        .andExpect(status().isOk());
    send(
            "/api/auth/reset-otp",
            Map.of(
                "email",
                "alex@example.edu",
                "code",
                codes.get("alex@example.edu"),
                "password",
                "NewCampusStrong!2026"),
            null)
        .andExpect(status().isOk());
    mvc.perform(get("/api/auth/me").session(alex.session())).andExpect(status().isUnauthorized());
    send("/api/auth/login", Map.of("email", "alex@example.edu", "password", password), null)
        .andExpect(status().isUnauthorized());
    send(
            "/api/auth/login",
            Map.of("email", "alex@example.edu", "password", "NewCampusStrong!2026"),
            null)
        .andExpect(status().isOk());
  }

  @Test
  void concurrentAcceptanceHasExactlyOneWinner() throws Exception {
    var owner = account("owner");
    var a = account("helpera");
    var b = account("helperb");
    long id = job(owner);
    try (var pool = Executors.newFixedThreadPool(2)) {
      List<Callable<Integer>> tasks =
          List.of(
              () ->
                  send("/api/jobs/" + id + "/actions", Map.of("action", "accept"), a.session())
                      .andReturn()
                      .getResponse()
                      .getStatus(),
              () ->
                  send("/api/jobs/" + id + "/actions", Map.of("action", "accept"), b.session())
                      .andReturn()
                      .getResponse()
                      .getStatus());
      var results = pool.invokeAll(tasks);
      assertThat(List.of(results.get(0).get(), results.get(1).get()))
          .containsExactlyInAnyOrder(200, 409);
    }
  }
}
