package com.javac.campus.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javac.campus.common.ApiException;
import java.net.URI;
import java.net.http.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/**
 * Codes never leave the email channel or private dev mailbox. Attempt counts commit even on
 * failure.
 *
 * Account OTPs are keyed by email AND purpose, so VERIFY and RESET state remain separate. These
 * are not job-delivery codes. The database persists expiry, attempts, and consumption; local
 * mode verifies a hash and managed mode checks Neon Auth email ownership.
 */
@Service
public class OtpService {
  // Parameterized JDBC operations on email_otps.
  private final JdbcTemplate db;
  // Slow local code hashing/matching; managed provider mode does not store its raw code.
  private final PasswordEncoder encoder;
  // Local/SMTP delivery abstraction used when Neon Auth is not configured.
  private final EmailDelivery mail;
  // Normalized ownership-provider base URL and the trusted frontend Origin sent to it.
  private final String neon, origin;
  // JSON request serialization and provider-response parsing.
  private final ObjectMapper json;
  // Cryptographically secure generator for local six-digit codes.
  private final SecureRandom random = new SecureRandom();

  /**
   * Injects persistence, hashing, delivery, JSON conversion, and provider settings. Normalizes
   * the provider base URL and rejects a configured non-HTTPS endpoint before sending any
   * ownership requests.
   */
  public OtpService(
      JdbcTemplate db,
      PasswordEncoder encoder,
      EmailDelivery mail,
      ObjectMapper json,
      @Value("${app.neon-auth-url:}") String neon,
      @Value("${app.frontend-url}") String origin) {
    this.db = db;
    this.encoder = encoder;
    this.mail = mail;
    this.json = json;
    this.neon = neon.replaceAll("/+$", "");
    this.origin = origin;
    if (!neon.isBlank() && !neon.startsWith("https://"))
      throw new IllegalStateException("NEON_AUTH_URL must use HTTPS.");
  }

  /**
   * Sends JSON to the configured Neon Auth endpoint over HTTPS with timeouts and the frontend
   * Origin. Parses only the ownership response needed here; it never forwards Java passwords or
   * adopts provider cookies. Maps provider/network failures to safe API messages and restores
   * interruption state when interrupted.
   */
  private com.fasterxml.jackson.databind.JsonNode remote(String path, Map<String, String> body) {
    try {
      var request =
          HttpRequest.newBuilder(URI.create(neon + path))
              .timeout(Duration.ofSeconds(15))
              .header("Content-Type", "application/json")
              .header("Origin", origin)
              .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
              .build();
      var response =
          HttpClient.newBuilder()
              .connectTimeout(Duration.ofSeconds(10))
              .build()
              .send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 200 && response.statusCode() < 300)
        return json.readTree(response.body());
      String providerCode = "";
      try {
        providerCode = json.readTree(response.body()).path("code").asText("");
      } catch (java.io.IOException ignored) {
        // Never expose the provider's raw response, which may contain private information.
      }
      if (providerCode.equals("INVALID_ORIGIN"))
        throw new ApiException(
            503,
            "Email verification is not configured for this site. The administrator must add the"
                + " site address to Neon Auth's trusted domains.");
      if (response.statusCode() == 429)
        throw new ApiException(429, "Please wait before requesting another code.");
      if (response.statusCode() >= 500)
        throw new ApiException(503, "The email verification service is unavailable.");
      if (path.equals("/email-otp/send-verification-otp"))
        throw new ApiException(
            503, "The email service could not send a verification code. Please try again later.");
      throw new ApiException(
          400, "The email code was not accepted. Check the code or request a new one.");
    } catch (ApiException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ApiException(503, "Email service was interrupted.");
    } catch (Exception e) {
      throw new ApiException(503, "Cannot reach the email verification service.");
    }
  }

  // Issue in an independent transaction so the persisted cooldown is shared across requests.
  /**
   * Runs in REQUIRES_NEW, independently of an outer registration/recovery transaction. Locks an
   * existing email/purpose record, enforces a 60-second cooldown, delivers a code, and replaces
   * its state with a five-minute expiry. Local mode saves a hash; managed mode saves
   * NEON_MANAGED because the provider owns the code.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void issue(String email, String purpose, String name) {
    var found =
        db.queryForList(
            "SELECT * FROM email_otps WHERE email=? AND purpose=? FOR UPDATE", email, purpose);
    if (!found.isEmpty()
        && Instant.parse(String.valueOf(found.getFirst().get("sent_at")))
            .isAfter(Instant.now().minusSeconds(60))) return;
    String code = String.format(Locale.ROOT, "%06d", random.nextInt(1000000));
    // Local verification stores a hash; managed verification stores a marker because Neon owns that
    // code.
    String hash = neon.isBlank() ? encoder.encode(code) : "NEON_MANAGED";
    if (!neon.isBlank()) {
      // Prove mailbox ownership for either a new or an existing Neon identity.
  /**
   * Sends JSON to the configured Neon Auth endpoint over HTTPS with timeouts and the frontend
   * Origin. Parses only the ownership response needed here; it never forwards Java passwords or
   * adopts provider cookies. Maps provider/network failures to safe API messages and restores
   * interruption state when interrupted.
   */
      remote("/email-otp/send-verification-otp", Map.of("email", email, "type", "sign-in"));
    } else
      mail.send(
          email,
          purpose.equals("VERIFY") ? "Your verification code" : "Your password reset code",
          code);
    db.update("DELETE FROM email_otps WHERE email=? AND purpose=?", email, purpose);
    db.update(
        "INSERT INTO email_otps(email,purpose,code_hash,expires_at,sent_at) VALUES(?,?,?,?,?)",
        email,
        purpose,
        hash,
        Instant.now().plusSeconds(300).toString(),
        Instant.now().toString());
  }

  // Failed checks must still commit their attempt count instead of giving unlimited retries.
  /**
   * Runs in its own transaction with noRollbackFor(ApiException.class), so failed guesses
   * retain their attempt count. Locks the email/purpose row, rejects used/expired/exhausted
   * state, increments attempts, checks the local hash or provider email ownership, and finally
   * marks the code used.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApiException.class)
  public void verify(String email, String purpose, String code) {
    var rows =
        db.queryForList(
            "SELECT * FROM email_otps WHERE email=? AND purpose=? FOR UPDATE", email, purpose);
    // The locked row serializes attempts and prevents concurrent requests from bypassing the limit.
    if (rows.isEmpty()) throw new ApiException(400, "Request a new code.");
    var row = rows.getFirst();
    if (Boolean.TRUE.equals(row.get("used"))
        || ((Number) row.get("attempts")).intValue() >= 5
        || !Instant.parse(String.valueOf(row.get("expires_at"))).isAfter(Instant.now()))
      throw new ApiException(400, "This code is expired or exhausted. Request a new one.");
    db.update(
        "UPDATE email_otps SET attempts=attempts+1 WHERE email=? AND purpose=?", email, purpose);
    if (neon.isBlank()) {
      if (!encoder.matches(code, String.valueOf(row.get("code_hash"))))
        throw new ApiException(400, "Incorrect code. You have at most five attempts.");
    } else {
      var result = remote("/sign-in/email-otp", Map.of("email", email, "otp", code));
      var user = result.path("user");
      if (user.isMissingNode()) user = result.path("data").path("user");
      if (!email.equalsIgnoreCase(user.path("email").asText())
          || !user.path("emailVerified").asBoolean(false))
        throw new ApiException(
            400, "The email ownership check did not complete. Request a new code.");
    }
    db.update("UPDATE email_otps SET used=TRUE WHERE email=? AND purpose=?", email, purpose);
  }
}
