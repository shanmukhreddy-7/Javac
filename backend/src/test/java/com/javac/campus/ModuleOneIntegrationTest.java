package com.javac.campus;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javac.campus.auth.EmailDelivery;
import com.javac.campus.user.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ModuleOneIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager entities;
  @MockitoBean EmailDelivery mail;
  private final String password = "CampusStrong!2026";
  private AtomicReference<String> delivered;

  @BeforeEach
  void mailbox() {
    delivered = new AtomicReference<>();
    doAnswer(
            i -> {
              delivered.set(i.getArgument(2));
              return null;
            })
        .when(mail)
        .send(anyString(), anyString(), anyString());
  }

  private Map<String, Object> registration(String email) {
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("fullName", "Alex Morgan");
    r.put("email", email);
    r.put("registrationNumber", email.split("@")[0] + "-2026");
    r.put("department", "Computer Science");
    r.put("studyYear", 2);
    r.put("password", password);
    r.put("confirmPassword", password);
    r.put("termsAccepted", true);
    return r;
  }

  private ResultActions send(String path, Object body) throws Exception {
    return mvc.perform(
        post(path)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body)));
  }

  private String create(String email) throws Exception {
    send("/api/auth/register", registration(email)).andExpect(status().isCreated());
    return delivered.get().split("token=")[1];
  }

  private MockHttpSession verifiedSession(String email) throws Exception {
    String token = create(email);
    send("/api/auth/verify-email", Map.of("token", token)).andExpect(status().isOk());
    return (MockHttpSession)
        send("/api/auth/login", Map.of("email", email, "password", password))
            .andExpect(status().isOk())
            .andReturn()
            .getRequest()
            .getSession();
  }

  @Test
  void validRegistrationStoresHashAndPendingAccountWithoutReturningToken() throws Exception {
    send("/api/auth/register", registration("alex@example.edu"))
        .andExpect(status().isCreated())
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("token"))));
    var user = users.findByEmail("alex@example.edu").orElseThrow();
    assertThat(user.getPasswordHash()).isNotEqualTo(password);
    assertThat(user.isEmailVerified()).isFalse();
    assertThat(user.getAccountStatus()).isEqualTo("PENDING");
    assertThat(delivered.get()).contains("/verify-email#token=");
  }

  @Test
  void duplicateEmailAndRegistrationNumberAreRejected() throws Exception {
    create("alex@example.edu");
    send("/api/auth/register", registration("alex@example.edu")).andExpect(status().isConflict());
    var r = registration("another@example.edu");
    r.put("registrationNumber", "alex-2026");
    send("/api/auth/register", r).andExpect(status().isConflict());
  }

  @Test
  void invalidDomainIsRejected() throws Exception {
    send("/api/auth/register", registration("alex@gmail.com")).andExpect(status().isBadRequest());
    assertThat(users.findByEmail("alex@gmail.com")).isEmpty();
  }

  @Test
  void weakPasswordIsRejected() throws Exception {
    var r = registration("alex@example.edu");
    r.put("password", "weak");
    r.put("confirmPassword", "weak");
    send("/api/auth/register", r)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.password").exists());
  }

  @Test
  void confirmationAndTermsAreRequired() throws Exception {
    var r = registration("alex@example.edu");
    r.put("termsAccepted", false);
    send("/api/auth/register", r).andExpect(status().isBadRequest());
    r.put("termsAccepted", true);
    r.put("confirmPassword", "wrong");
    send("/api/auth/register", r).andExpect(status().isBadRequest());
  }

  @Test
  void verificationActivatesAccountAndCannotBeReplayed() throws Exception {
    var token = create("alex@example.edu");
    send("/api/auth/verify-email", Map.of("token", token)).andExpect(status().isOk());
    assertThat(users.findByEmail("alex@example.edu").orElseThrow().isEmailVerified()).isTrue();
    send("/api/auth/verify-email", Map.of("token", token)).andExpect(status().isBadRequest());
  }

  @Test
  void expiredVerificationIsRejected() throws Exception {
    var token = create("alex@example.edu");
    entities.flush();
    jdbc.update("update email_verification_tokens set expires_at = '2000-01-01 00:00:00'");
    entities.clear();
    send("/api/auth/verify-email", Map.of("token", token)).andExpect(status().isBadRequest());
  }

  @Test
  void correctLoginPersistsSessionAcrossRequests() throws Exception {
    var session = verifiedSession("alex@example.edu");
    mvc.perform(get("/api/auth/me").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("alex@example.edu"))
        .andExpect(jsonPath("$.passwordHash").doesNotExist());
  }

  @Test
  void incorrectAndUnverifiedLoginAreRejected() throws Exception {
    create("alex@example.edu");
    send("/api/auth/login", Map.of("email", "alex@example.edu", "password", "bad"))
        .andExpect(status().isUnauthorized());
    send("/api/auth/login", Map.of("email", "alex@example.edu", "password", password))
        .andExpect(status().isForbidden());
  }

  @Test
  void csrfAndAuthenticationProtectProfileUpdate() throws Exception {
    var update =
        Map.of(
            "fullName",
            "Attacker Name",
            "department",
            "Sciences",
            "studyYear",
            2,
            "bio",
            "",
            "preferredLocation",
            "");
    mvc.perform(
            patch("/api/students/me")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(update)))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(registration("alex@example.edu"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void ownProfileCanBeEditedWithoutChangingAnotherStudent() throws Exception {
    var alex = verifiedSession("alex@example.edu");
    var sam = verifiedSession("sam@example.edu");
    mvc.perform(
            patch("/api/students/me")
                .session(alex)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "fullName",
                            "Alex Updated",
                            "department",
                            "Sciences",
                            "studyYear",
                            3,
                            "bio",
                            "I enjoy Java.",
                            "preferredLocation",
                            "Library"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullName").value("Alex Updated"));
    mvc.perform(get("/api/students/me").session(sam))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullName").value("Alex Morgan"));
  }

  @Test
  void skillsAreAssignedAndUnknownSkillsRejected() throws Exception {
    var session = verifiedSession("alex@example.edu");
    mvc.perform(
            put("/api/students/me/skills")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"skillIds\":[1,2]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.skills.length()").value(2));
    mvc.perform(
            put("/api/students/me/skills")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"skillIds\":[99999]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void resendHasSixtySecondCooldownAndDoesNotRevealAccountExistence() throws Exception {
    create("alex@example.edu");
    send("/api/auth/resend-verification", Map.of("email", "alex@example.edu"))
        .andExpect(status().isOk());
    verify(mail, times(1)).send(anyString(), anyString(), anyString());
    send("/api/auth/resend-verification", Map.of("email", "unknown@example.edu"))
        .andExpect(status().isOk());
  }

  @Test
  void passwordResetIsSingleUseAndInvalidatesPreviousSession() throws Exception {
    var session = verifiedSession("alex@example.edu");
    send("/api/auth/forgot-password", Map.of("email", "alex@example.edu"))
        .andExpect(status().isOk());
    var token = delivered.get().split("token=")[1];
    send("/api/auth/reset-password", Map.of("token", token, "password", "NewCampusStrong!2026"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    send("/api/auth/reset-password", Map.of("token", token, "password", "AnotherCampus!2026"))
        .andExpect(status().isBadRequest());
    send("/api/auth/login", Map.of("email", "alex@example.edu", "password", "NewCampusStrong!2026"))
        .andExpect(status().isOk());
  }

  @Test
  void changePasswordChecksCurrentPassword() throws Exception {
    var session = verifiedSession("alex@example.edu");
    mvc.perform(
            post("/api/auth/change-password")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of("currentPassword", "wrong", "password", "NewCampusStrong!2026"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void logoutEndsSession() throws Exception {
    var session = verifiedSession("alex@example.edu");
    mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isOk());
    assertThat(session.isInvalid()).isTrue();
  }

  @Test
  void realCsrfHandshakeAndLoginRotateSecurityToken() throws Exception {
    var token = create("alex@example.edu");
    send("/api/auth/verify-email", Map.of("token", token)).andExpect(status().isOk());
    var handshake = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
    var session = (MockHttpSession) handshake.getRequest().getSession();
    String csrfToken =
        json.readTree(handshake.getResponse().getContentAsString()).get("token").asText();
    String oldId = session.getId();
    mvc.perform(
            post("/api/auth/login")
                .session(session)
                .header("X-CSRF-TOKEN", csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of("email", "alex@example.edu", "password", password))))
        .andExpect(status().isOk());
    assertThat(session.getId()).isNotEqualTo(oldId);
    mvc.perform(post("/api/auth/logout").session(session).header("X-CSRF-TOKEN", csrfToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void inactiveAccountsAndProfileOwnershipTamperingAreRejected() throws Exception {
    var session = verifiedSession("alex@example.edu");
    mvc.perform(
            patch("/api/students/me")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"userId\":999,\"fullName\":\"Other"
                        + " Student\",\"department\":\"Sciences\",\"studyYear\":2,\"bio\":\"\",\"preferredLocation\":\"\"}"))
        .andExpect(status().isBadRequest());
    entities.flush();
    jdbc.update("update users set account_status='SUSPENDED' where email='alex@example.edu'");
    entities.clear();
    mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    send("/api/auth/login", Map.of("email", "alex@example.edu", "password", password))
        .andExpect(status().isForbidden());
  }
}
