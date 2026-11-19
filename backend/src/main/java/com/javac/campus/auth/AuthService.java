package com.javac.campus.auth;

import com.javac.campus.auth.AuthDtos.*;
import com.javac.campus.security.StudentPrincipal;
import java.util.List;

/**
 * Authentication contract used by controllers without depending on its implementation.
 * Implementations own account rules; the controller owns HTTP and session handling.
 *
 * This interface is an abstraction: controllers know these operations, while AuthServiceImpl
 * supplies the implementation. Most methods return void because they change account state or
 * initiate email delivery; login returns a minimal identity for session creation.
 */
public interface AuthService {
  /**
   * Establishes ownership with an email and six-digit VERIFY-purpose code, then activates the
   * pending Java account. This does not complete a delivery job.
   */
  void verifyOtp(String email, String code);

  /**
   * Checks a RESET-purpose account code before storing the new encoded password and revoking
   * older sessions. Used by the OTP recovery path.
   */
  void resetOtp(String email, String code, String password);

  /**
   * Checks university/identity rules, creates account and profile records plus welcome credit,
   * and initiates verification. Successful creation does not mean the account is verified yet.
   */
  void register(Register request);

  /**
   * Validates password and account eligibility and returns a minimal StudentPrincipal. The
   * controller, rather than this service contract, creates/persists the HTTP login session.
   */
  StudentPrincipal login(Login request);

  /**
   * Consumes a raw single-use email-verification link token and activates its pending account.
   * The implementation hashes the secret before repository lookup.
   */
  void verify(String token);

  /**
   * Requests another verification message for an eligible pending account. Ineligible/missing
   * accounts and cooldown cases do not disclose account state to the caller.
   */
  void resend(String email);

  /**
   * Initiates recovery through the configured OTP or link path for a verified active account;
   * replies are intentionally generic to protect account privacy.
   */
  void forgotPassword(String email);

  /**
   * Consumes a valid recovery link token, changes the encoded password, and invalidates older
   * sessions through the account credential version.
   */
  void resetPassword(Reset request);

  /**
   * Requires the signed-in account ID and a valid current password before replacing credentials.
   * The controller also invalidates its own session afterward.
   */
  void changePassword(Long id, Change request);

  /**
   * Returns the normalized allowed university-domain list used by registration and public form
   * configuration.
   */
  List<String> approvedDomains();
}
