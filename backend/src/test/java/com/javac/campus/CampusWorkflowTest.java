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
import org.springframework.mail.MailSendException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;

/**
 * Exercises full OTP, job, moderation, and wallet workflows through Spring MockMvc and an
 * isolated H2 database. Mocked EmailDelivery captures test codes instead of sending mail.
 * Tests query SQL state as well as HTTP results to prove that transactions and permission
 * checks actually persisted the intended outcome.
 */
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

  /**
   * Test fixture pairing the persisted user ID with its signed-in mock session; it is not a
   * production account DTO.
   */
  record Account(long id, MockHttpSession session) {}

  /**
   * Deletes dependent test rows before parent rows to respect foreign keys, clears captured
   * codes, and replaces mail delivery with a callback that extracts six-digit codes. Each test
   * starts with clean workflow data; the shared skill directory is retained.
   */
  @BeforeEach
  void prepare() {
    for (String table :
        List.of(
            "job_reviews",
            "job_reports",
            "job_messages",
            "notifications",
            "wallet_transactions",
            "wallet_withdrawal_requests",
            "wallet_topup_requests",
            "wallet_accounts",
            "job_attachments",
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
              String text=i.getArgument(2);
              var matcher=java.util.regex.Pattern.compile("(?<![0-9])([0-9]{6})(?![0-9])").matcher(text);
              if(matcher.find()) codes.put(i.getArgument(0), matcher.group(1));
              return null;
            })
        .when(mail)
        .send(anyString(), anyString(), anyString());
  }

  /**
   * Builds a JSON POST with a test CSRF token and optional mock login session. Returns
   * ResultActions so each scenario can assert HTTP status/body.
   */
  ResultActions send(String path, Object body, MockHttpSession session) throws Exception {
    var request =
        post(path)
            .with(csrf())
            .contentType("application/json")
            .content(json.writeValueAsString(body));
    if (session != null) request.session(session);
    return mvc.perform(request);
  }

  /**
   * Parses a MockMvc response body into a JsonNode so assertions can inspect fields without
   * defining a test DTO.
   */
  JsonNode data(ResultActions a) throws Exception {
    return json.readTree(a.andReturn().getResponse().getContentAsString());
  }

  /**
   * Creates a valid synthetic student through the real registration endpoint. The test mail
   * callback captures the ownership code for the next verification step.
   */
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

  /**
   * Registers, verifies with the captured code, and logs in a synthetic account. Returns its
   * database user ID and HTTP session for authenticated workflow requests.
   */
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

  /**
   * Posts a food-collection job for the supplied account with a future deadline and illustrative
   * reward. Extracts its generated ID for later helper actions.
   */
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

  /**
   * Submits an action containing only its name and checks the expected HTTP status; used for
   * acceptance/start/permission scenarios.
   */
  void act(Account a, long id, String action, int expected) throws Exception {
    send("/api/jobs/" + id + "/actions", Map.of("action", action), a.session())
        .andExpect(status().is(expected));
  }

  /**
   * Builds an action payload with optional checklist step and handoff code. Returns the response
   * so the caller can inspect successful updates or otp_error.
   */
  ResultActions action(Account a,long id,String action,String step,String otp) throws Exception {
    var body=new HashMap<String,Object>();body.put("action",action);if(step!=null)body.put("step",step);if(otp!=null)body.put("otp",otp);
    return send("/api/jobs/"+id+"/actions",body,a.session());
  }

  /**
   * Asserts that a generated account code is six digits but is not stored as plaintext. Five
   * wrong submissions must persist five attempts and block even the correct code afterward.
   */
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

  /**
   * Confirms that an immediate resend does not send another email, then changes the stored
   * expiry into the past and proves the old code is rejected.
   */
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

  /**
   * Runs requester/helper/stranger scenarios through acceptance, private chat, ordered delivery
   * progress, wrong/correct handoff, offline collection, and feedback. Checks forbidden actors,
   * redacted hashes, duplicate collection/review rejection, and closed conversation behavior.
   */
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
    String deliveryCode=codes.get("owner@example.edu");
    assertThat(deliveryCode).matches("[0-9]{6}");
    String wrong=deliveryCode.equals("000000")?"999999":"000000";
    action(helper,id,"progress","PICKED_UP",null).andExpect(status().isOk());
    action(helper,id,"progress","ON_THE_WAY",null).andExpect(status().isOk());
    action(helper,id,"progress","AT_BLOCK",null).andExpect(status().isOk());
    var publicDetail=mvc.perform(get("/api/jobs/"+id).session(helper.session())).andExpect(status().isOk()).andReturn();
    assertThat(json.readTree(publicDetail.getResponse().getContentAsString()).has("completion_otp_hash")).isFalse();
    action(helper,id,"complete",null,wrong).andExpect(status().isOk()).andExpect(jsonPath("$.otp_error").exists());
    action(owner,id,"complete",null,deliveryCode).andExpect(status().isConflict());
    action(helper,id,"complete",null,deliveryCode).andExpect(status().isOk());
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

  /**
   * Starts with the welcome balance, submits proof/UTR without immediate credit, approves as
   * admin, reserves a withdrawal, and rejects it to restore funds. Assertions distinguish
   * pending review from actual balance changes.
   */
  @Test
  void welcomeCreditTopupAndWithdrawalAreAdminReviewed() throws Exception {
    var student=account("walletstudent");var admin=account("admin");
    assertThat(data(mvc.perform(get("/api/wallet").session(student.session()))).get("balance").decimalValue()).isEqualByComparingTo("100.00");
    var proof=new MockMultipartFile("proof","proof.jpg","image/jpeg",new byte[]{(byte)255,(byte)216,(byte)255,(byte)224,1,2});
    mvc.perform(multipart("/api/wallet/topups").file(proof).param("amount","250.00").param("utr","abc123456789").session(student.session()).with(csrf()))
        .andExpect(status().isCreated());
    assertThat(data(mvc.perform(get("/api/wallet").session(student.session()))).get("balance").decimalValue()).isEqualByComparingTo("100.00");
    long topupId=db.queryForObject("SELECT id FROM wallet_topup_requests WHERE utr='ABC123456789'",Long.class);
    mvc.perform(get("/api/admin/wallet/topups/"+topupId+"/proof").session(admin.session())).andExpect(status().isOk()).andExpect(header().string("X-Content-Type-Options","nosniff"));
    send("/api/admin/wallet/topups/"+topupId+"/review",Map.of("status","APPROVED","note","Verified"),admin.session()).andExpect(status().isOk());
    assertThat(data(mvc.perform(get("/api/wallet").session(student.session()))).get("balance").decimalValue()).isEqualByComparingTo("350.00");
    send("/api/wallet/withdrawals",Map.of("amount",40,"upiId","student@bank"),student.session()).andExpect(status().isCreated());
    assertThat(data(mvc.perform(get("/api/wallet").session(student.session()))).get("balance").decimalValue()).isEqualByComparingTo("310.00");
    long withdrawalId=db.queryForObject("SELECT id FROM wallet_withdrawal_requests",Long.class);
    send("/api/admin/wallet/withdrawals/"+withdrawalId+"/review",Map.of("status","REJECTED","note","Test refund"),admin.session()).andExpect(status().isOk());
    assertThat(data(mvc.perform(get("/api/wallet").session(student.session()))).get("balance").decimalValue()).isEqualByComparingTo("350.00");
  }

  /**
   * Confirms students cannot read admin data, then reports/hides a job and suspends its owner as
   * admin. Reusing the owner's old session must fail after the account state changes.
   */
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

  /**
   * Performs OTP password recovery, proves an older session and old password fail, and confirms
   * the replacement password works.
   */
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

  /**
   * Sends two helper accept requests concurrently using a thread pool. Exactly one must return
   * 200 and the other 409, verifying the database row lock prevents double acceptance.
   */
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

  /** Regression: delivery SMTP failure must not roll back a helper's acceptance. */
  @Test
  void smtpFailureStillAllowsSecureInAppHandoffAndCompletion() throws Exception {
    var owner = account("owner");
    var helper = account("helper");
    var stranger = account("stranger");
    long id = job(owner);
    doThrow(new MailSendException("SMTP is unavailable"))
        .when(mail).send(eq("owner@example.edu"), eq("JAVAC delivery confirmation code"), anyString());

    act(helper, id, "accept", 200);
    assertThat(db.queryForObject("SELECT status FROM campus_jobs WHERE id=?", String.class, id))
        .isEqualTo("ACCEPTED");
    // Neither the helper nor an unrelated student can retrieve the requester's secret.
    send("/api/jobs/" + id + "/handoff-code", null, helper.session()).andExpect(status().isForbidden());
    send("/api/jobs/" + id + "/handoff-code", null, stranger.session()).andExpect(status().isForbidden());
    send("/api/jobs/" + id + "/handoff-code", null, owner.session()).andExpect(status().isConflict());
    act(helper, id, "start", 200);
    for (String step : List.of("PICKED_UP", "ON_THE_WAY", "AT_BLOCK"))
      action(helper, id, "progress", step, null).andExpect(status().isOk());

    mvc.perform(post("/api/jobs/" + id + "/handoff-code").session(owner.session()))
        .andExpect(status().isForbidden());
    var issued = send("/api/jobs/" + id + "/handoff-code", null, owner.session())
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "private, no-store"));
    String code = data(issued).get("code").asText();
    assertThat(code).matches("[0-9]{6}");
    assertThat(db.queryForObject("SELECT completion_otp_hash FROM campus_jobs WHERE id=?", String.class, id))
        .doesNotContain(code);
    for (var viewer : List.of(owner, helper, stranger)) {
      mvc.perform(get("/api/jobs/" + id).session(viewer.session()))
          .andExpect(jsonPath("$.code").doesNotExist())
          .andExpect(jsonPath("$.completion_otp_hash").doesNotExist());
    }
    action(helper, id, "complete", null, code).andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));
    send("/api/jobs/" + id + "/handoff-code", null, owner.session()).andExpect(status().isConflict());
    assertThat(db.queryForObject("SELECT completion_otp_hash FROM campus_jobs WHERE id=?", String.class, id)).isNull();
    // Completing a job still does not automatically transfer its reward into a wallet.
    mvc.perform(get("/api/wallet").session(helper.session()))
        .andExpect(jsonPath("$.balance").value(100));
  }

  /** Reissue recovers exhausted/expired codes without allowing stale codes or hidden jobs. */
  @Test
  void requesterCanRecoverLockedCodeButCannotIssueForHiddenOrCancelledJobs() throws Exception {
    var owner = account("owner");
    var helper = account("helper");
    long id = job(owner);
    act(helper, id, "accept", 200);
    act(helper, id, "start", 200);
    for (String step : List.of("PICKED_UP", "ON_THE_WAY", "AT_BLOCK"))
      action(helper, id, "progress", step, null).andExpect(status().isOk());
    String code = data(send("/api/jobs/" + id + "/handoff-code", null, owner.session())).get("code").asText();
    String wrong = code.equals("000000") ? "999999" : "000000";
    for (int i = 0; i < 5; i++)
      action(helper, id, "complete", null, wrong).andExpect(jsonPath("$.otp_error").exists());
    action(helper, id, "complete", null, code).andExpect(jsonPath("$.otp_error").exists());
    db.update("UPDATE campus_jobs SET completion_otp_expires_at=? WHERE id=?", Instant.now().minusSeconds(1).toString(), id);
    String fresh = data(send("/api/jobs/" + id + "/handoff-code", null, owner.session())).get("code").asText();
    assertThat(db.queryForObject("SELECT completion_otp_attempts FROM campus_jobs WHERE id=?", Integer.class, id)).isZero();
    action(helper, id, "complete", null, fresh).andExpect(jsonPath("$.status").value("COMPLETED"));

    long cancelled = job(owner);
    act(owner, cancelled, "cancel", 200);
    send("/api/jobs/" + cancelled + "/handoff-code", null, owner.session()).andExpect(status().isConflict());
    db.update("UPDATE campus_jobs SET hidden=TRUE,status='DELIVERED' WHERE id=?", cancelled);
    send("/api/jobs/" + cancelled + "/handoff-code", null, owner.session()).andExpect(status().isConflict());
  }

  /** Xerox uses private bytes and an extra ordered printing stage before delivery. */
  @Test
  void xeroxFileIsPrivateAndPrintingStepsCannotBeSkipped() throws Exception {
    var owner = account("owner");
    var helper = account("helper");
    var stranger = account("stranger");
    var request = new MockPart("request", json.writeValueAsBytes(Map.of(
        "title", "Print notes", "description", "Print my lecture notes", "category", "Xerox",
        "reward", 20, "pickup", "Library", "destination", "Academic block",
        "deadline", Instant.now().plusSeconds(3600).toString(), "xeroxMode", "BLACK_WHITE", "xeroxCopies", 2)));
    request.getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
    byte[] bytes = "%PDF-1.4 fictional test document".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    var file = new MockMultipartFile("file", "notes.pdf", "application/pdf", bytes);
    long id = data(mvc.perform(multipart("/api/jobs").file(file).part(request)
        .session(owner.session()).with(csrf())).andExpect(status().isCreated())).get("id").asLong();
    mvc.perform(get("/api/jobs/" + id + "/file").session(stranger.session())).andExpect(status().isForbidden());
    act(helper, id, "accept", 200);
    mvc.perform(get("/api/jobs/" + id + "/file").session(helper.session()))
        .andExpect(status().isOk()).andExpect(content().bytes(bytes))
        .andExpect(header().string("Cache-Control", "private, no-store"));
    act(helper, id, "start", 200);
    action(helper, id, "progress", "ON_THE_WAY", null).andExpect(status().isConflict());
    for (String step : List.of("FILE_RECEIVED", "XEROX_COMPLETE", "ON_THE_WAY", "AT_BLOCK"))
      action(helper, id, "progress", step, null).andExpect(status().isOk());
    String code = data(send("/api/jobs/" + id + "/handoff-code", null, owner.session())).get("code").asText();
    action(helper, id, "complete", null, code).andExpect(jsonPath("$.status").value("COMPLETED"));
  }
}
