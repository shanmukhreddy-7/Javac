package com.javac.campus.auth;

import jakarta.validation.constraints.*;

/**
 * Immutable request and response records for account endpoints.
 * Jakarta Validation annotations reject malformed fields before service rules run.
 *
 * A record supplies a constructor and read accessors such as email() automatically. Validation
 * annotations describe allowed inputs; they run when a controller uses @Valid. They do not
 * replace checks such as university-domain approval, matching confirmation, or account state.
 */
public final class AuthDtos {
  /**
   * Private constructor prevents instantiation of this holder of static helpers or DTO types.
   */
  private AuthDtos() {}

  // Lookaheads require lowercase, uppercase, digit, and symbol; total length is 12–64.
  public static final String PASSWORD =
      "^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^a-zA-Z0-9]).{12,64}$";

  /**
   * Registration input: identity, university details, password/confirmation, and explicit terms
   * consent. Field constraints check syntax; AuthServiceImpl checks approved domain,
   * duplicates, and matching passwords.
   */
  public record Register(
      @NotBlank @Size(min = 2, max = 80) String fullName,
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank
          @Pattern(regexp = "[A-Za-z0-9-]{3,40}", message = "Use 3–40 letters, numbers or hyphens.")
          String registrationNumber,
      @NotBlank @Size(max = 80) String department,
      @Min(1) @Max(6) int studyYear,
      @NotBlank
          @Pattern(
              regexp = PASSWORD,
              message = "Use 12–64 characters with upper/lowercase, a number and a symbol.")
          String password,
      @NotBlank String confirmPassword,
      @AssertTrue(message = "Accept the terms to continue.") boolean termsAccepted) {}

  /**
   * Login accepts an email/password pair. It does not accept a role or account ID from the
   * client.
   */
  public record Login(
      @NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 64) String password) {}

  /**
   * Email-only input shared by resend and recovery endpoints. A valid email shape does not
   * prove registration or ownership.
   */
  public record EmailRequest(@NotBlank @Email @Size(max = 254) String email) {}

  /**
   * Carries a raw verification-link token received from the emailed frontend URL. The service
   * hashes it before lookup.
   */
  public record TokenRequest(@NotBlank @Size(max = 128) String token) {}

  /**
   * Link-based password recovery input: a raw single-use token plus a password meeting the
   * shared policy.
   */
  public record Reset(
      @NotBlank @Size(max = 128) String token,
      @NotBlank
          @Pattern(
              regexp = PASSWORD,
              message = "Use 12–64 characters with upper/lowercase, a number and a symbol.")
          String password) {}

  /**
   * Authenticated password-change input: current password proves possession; replacement must
   * meet the shared policy.
   */
  public record Change(
      @NotBlank @Size(max = 64) String currentPassword,
      @NotBlank
          @Pattern(
              regexp = PASSWORD,
              message = "Use 12–64 characters with upper/lowercase, a number and a symbol.")
          String password) {}

  /**
   * Email and exactly six decimal digits for an account ownership check. Delivery handoff uses
   * MarketDtos.Action instead.
   */
  public record Otp(
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Pattern(regexp = "[0-9]{6}") String code) {}

  /**
   * Account recovery input combines email, a six-digit RESET code, and the new policy-compliant
   * password.
   */
  public record ResetOtp(
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Pattern(regexp = "[0-9]{6}") String code,
      @NotBlank @Pattern(regexp = PASSWORD) String password) {}

  /**
   * Simple response message wrapper; Spring serializes it as a JSON object with a message
   * property.
   */
  public record Message(String message) {}
}
