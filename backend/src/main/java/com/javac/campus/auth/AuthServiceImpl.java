package com.javac.campus.auth;

import com.javac.campus.auth.AuthDtos.*;
import com.javac.campus.common.ApiException;
import com.javac.campus.profile.*;
import com.javac.campus.security.StudentPrincipal;
import com.javac.campus.user.*;
import com.javac.campus.marketplace.WalletService;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional account rules: approved domains, hashed passwords, verification, and recovery.
 * Java owns application credentials and sessions even when Neon Auth verifies email ownership.
 *
 * @Service lets Spring inject this implementation. Class-level @Transactional groups its JPA and
 * JDBC account writes; Hibernate dirty checking persists modifications to loaded entities.
 * OtpService deliberately uses independent transactions for cooldowns and failed-attempt state.
 */
@Service
@Transactional
public class AuthServiceImpl implements AuthService {
  // JPA account queries, including row-locked credential operations.
  private final UserRepository users;
  // Student profile persistence, separate from login credentials.
  private final StudentProfileRepository profiles;
  // Verification-link hash lookup, consumption, and resend history.
  private final VerificationTokenRepository verification;
  // Password-reset link lookup and invalidation.
  private final PasswordResetTokenRepository resets;
  // Configured PBKDF2 verifier for raw password/code comparisons.
  private final PasswordEncoder encoder;
  // Profile-selected local or SMTP delivery for link messages.
  private final EmailDelivery email;
  // Normalized, deduplicated allowed university email domains.
  private final List<String> domains;
  // Frontend base URL used in emailed verification/recovery links.
  private final String frontend;
  // Precomputed encoded random value for a realistic comparison when an account is absent.
  private final String dummyHash;
  // Account-code service with independent attempt/cooldown transactions.
  private final OtpService otp;
  // Creates the one-time wallet credit alongside a new account.
  private final WalletService wallet;
  // Configured account-verification mode and trusted administrator email.
  private final String verificationMode, adminEmail;

  /**
   * Spring injects repositories, encoders, mail/OTP services, wallet operations, and
   * configuration. Splits/deduplicates allowed domains, removes trailing frontend slashes, and
   * precomputes a dummy hash for missing-account password comparisons.
   */
  public AuthServiceImpl(
      UserRepository users,
      StudentProfileRepository profiles,
      VerificationTokenRepository verification,
      PasswordResetTokenRepository resets,
      PasswordEncoder encoder,
      EmailDelivery email,
      @Value("${app.approved-domains}") String domains,
      @Value("${app.frontend-url}") String frontend,
      OtpService otp,
      @Value("${app.verification-mode:otp}") String verificationMode,
      @Value("${app.admin-email}") String adminEmail,
      WalletService wallet) {
    this.otp = otp;
    this.wallet = wallet;
    this.verificationMode = verificationMode;
    this.adminEmail = adminEmail;
    this.users = users;
    this.profiles = profiles;
    this.verification = verification;
    this.resets = resets;
    this.encoder = encoder;
    this.email = email;
    this.domains =
        Arrays.stream(domains.split(","))
            .map(String::trim)
            .map(String::toLowerCase)
            .filter(s -> !s.isBlank())
            .distinct()
            .toList();
    this.frontend = frontend.replaceAll("/+$", "");
    this.dummyHash = encoder.encode(TokenGenerator.create());
  }

  /**
   * Removes surrounding spaces and lowercases email using Locale.ROOT. Consistent normalization
   * makes account lookups and uniqueness checks independent of letter case and machine locale.
   */
  private String normalize(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  /**
   * Returns the configured immutable list of permitted university domains. Registration and the
   * public configuration endpoint read the same server-owned list.
   */
  public List<String> approvedDomains() {
    return domains;
  }

  /**
   * Creates a PENDING user, linked student profile, and one-time welcome wallet credit inside
   * the service transaction. It checks allowed domain, password confirmation, and duplicate
   * identity before saving; only an encoded password is stored. Verification delivery then uses
   * the configured link or OTP path.
   */
  public void register(Register r) {
    String address = normalize(r.email());
    if (domains.isEmpty())
      throw new ApiException(
          503,
          "University email domains have not been configured. Please contact the administrator.");
    if (!domains.contains(address.substring(address.lastIndexOf('@') + 1)))
      throw new ApiException(400, "Use an email from an approved university domain.");
    if (!r.password().equals(r.confirmPassword()))
      throw new ApiException(400, "Passwords do not match.");
    if (users.existsByEmail(address)
        || profiles.existsByRegistrationNumber(r.registrationNumber().toUpperCase(Locale.ROOT)))
      throw new ApiException(409, "This email or registration number is already registered.");
    var user = users.saveAndFlush(new User(address, encoder.encode(r.password())));
    profiles.saveAndFlush(
        new StudentProfile(
            user,
            r.fullName().trim(),
            r.registrationNumber().toUpperCase(Locale.ROOT),
            r.department().trim(),
            r.studyYear()));
    wallet.createWelcomeCredit(user.getId());
    if (verificationMode.equals("link")) issueVerification(user);
    else otp.issue(address, "VERIFY", r.fullName());
  }

  /**
   * Looks up the normalized email and verifies its password hash, email-verification flag, and
   * ACTIVE status. Returns identity only: AuthController performs session creation. Role comes
   * from configured administrator email, never a registration field.
   */
  public StudentPrincipal login(Login r) {
    var user = users.findByEmail(normalize(r.email())).orElse(null);
    // Use a real hash comparison even for a missing account, reducing the timing difference.
    boolean matches =
        encoder.matches(r.password(), user == null ? dummyHash : user.getPasswordHash());
    if (user == null || !matches) throw new ApiException(401, "Incorrect email or password.");
    if (!user.isEmailVerified())
      throw new ApiException(403, "Verify your university email before logging in.");
    if (!"ACTIVE".equals(user.getAccountStatus()))
      throw new ApiException(403, "Your account is not active. Contact the administrator.");
    // The configured admin address determines the role; registration input cannot choose it.
    user.setRole(user.getEmail().equalsIgnoreCase(adminEmail) ? "ADMIN" : "STUDENT");
    return new StudentPrincipal(user.getId(), user.getEmail(), user.getCredentialVersion());
  }

  /**
   * Invalidates older unused verification links, generates a random raw token, and saves its
   * SHA-256 lookup hash with a 30-minute expiry. Only the emailed frontend link contains the
   * raw secret.
   */
  private void issueVerification(User user) {
    verification.findByUserIdAndUsedAtIsNull(user.getId()).forEach(ExpiringToken::consume);
    String raw = TokenGenerator.create();
    verification.save(
        new VerificationToken(
            user, TokenGenerator.hash(raw), Instant.now().plus(Duration.ofMinutes(30))));
    email.send(
        user.getEmail(), "Verify your university email", frontend + "/verify-email#token=" + raw);
  }

  /**
   * Hashes the submitted link token for a locked repository lookup, rejects expired/consumed
   * tokens and non-pending accounts, then consumes the token and activates its user. Managed
   * JPA entities are updated when this transaction commits.
   */
  public void verify(String raw) {
    var token =
        verification
            .findByTokenHash(TokenGenerator.hash(raw))
            .orElseThrow(
                () -> new ApiException(400, "This verification link is invalid or expired."));
    if (!token.isValid())
      throw new ApiException(400, "This verification link is invalid or expired.");
    if (!"PENDING".equals(token.getUser().getAccountStatus()))
      throw new ApiException(400, "This account cannot be verified.");
    token.consume();
    token.getUser().verify();
  }

  /**
   * Locks the account before checking eligibility and link resend history. Ineligible accounts
   * return silently; recent link records trigger a 60-second cooldown, while OTP mode also
   * enforces its own database-backed cooldown.
   */
  public void resend(String address) {
    var user = users.lockByEmail(normalize(address)).orElse(null);
    if (user == null || user.isEmailVerified() || !"PENDING".equals(user.getAccountStatus()))
      return;
    var last = verification.findFirstByUserIdOrderByCreatedAtDesc(user.getId());
    // Generic response also covers cooldown to avoid revealing account existence.
    if (last.isPresent() && last.get().getCreatedAt().isAfter(Instant.now().minusSeconds(60)))
      return;
    if (verificationMode.equals("link")) issueVerification(user);
    else otp.issue(user.getEmail(), "VERIFY", "JAVAC student");
  }

  /**
   * Locks and checks a verified ACTIVE user. OTP mode delegates to OtpService; link mode
   * enforces cooldown, consumes older reset links, and emails a new 15-minute token.
   * Missing/ineligible users are intentionally indistinguishable to the caller.
   */
  public void forgotPassword(String address) {
    var user = users.lockByEmail(normalize(address)).orElse(null);
    if (user == null || !user.isEmailVerified() || !"ACTIVE".equals(user.getAccountStatus()))
      return;
    if (!verificationMode.equals("link")) {
      otp.issue(user.getEmail(), "RESET", "JAVAC student");
      return;
    }
    var last = resets.findFirstByUserIdOrderByCreatedAtDesc(user.getId());
    if (last.isPresent() && last.get().getCreatedAt().isAfter(Instant.now().minusSeconds(60)))
      return;
    resets.findByUserIdAndUsedAtIsNull(user.getId()).forEach(ExpiringToken::consume);
    String raw = TokenGenerator.create();
    resets.save(
        new PasswordResetToken(user, TokenGenerator.hash(raw), Instant.now().plusSeconds(900)));
    email.send(user.getEmail(), "Reset your password", frontend + "/reset-password#token=" + raw);
  }

  /**
   * Uses a locked, hashed reset-token lookup and then locks the user account so credential
   * changes serialize. Consumes the token and stores an encoded password; User.changePassword
   * also increments the session-revocation version.
   */
  public void resetPassword(Reset r) {
    var token =
        resets
            .findByTokenHash(TokenGenerator.hash(r.token()))
            .orElseThrow(() -> new ApiException(400, "This reset link is invalid or expired."));
    if (!token.isValid()) throw new ApiException(400, "This reset link is invalid or expired.");
    // Serialize all credential changes on the same account.
    var user = users.lockByEmail(token.getUser().getEmail()).orElseThrow();
    if (!"ACTIVE".equals(user.getAccountStatus()))
      throw new ApiException(403, "This account is not active.");
    token.consume();
    user.changePassword(encoder.encode(r.password()));
  }

  /**
   * Locks a PENDING application account, asks OtpService to establish mailbox ownership, and
   * then activates the user. Provider authentication is only ownership proof: Java remains the
   * owner of application passwords and sessions.
   */
  public void verifyOtp(String address, String code) {
    var user =
        users
            .lockByEmail(normalize(address))
            .orElseThrow(() -> new ApiException(400, "Account is not awaiting verification."));
    if (!"PENDING".equals(user.getAccountStatus()))
      throw new ApiException(400, "Account is not awaiting verification.");
    otp.verify(user.getEmail(), "VERIFY", code);
    user.verify();
  }

  /**
   * Requires a verified ACTIVE user and a successful RESET-purpose OTP check before changing
   * credentials. Consumes old unused reset links too, so another recovery path cannot reuse an
   * older link.
   */
  public void resetOtp(String address, String code, String password) {
    var user =
        users
            .lockByEmail(normalize(address))
            .orElseThrow(() -> new ApiException(400, "Invalid reset request."));
    if (!user.isEmailVerified() || !"ACTIVE".equals(user.getAccountStatus()))
      throw new ApiException(400, "Invalid reset request.");
    otp.verify(user.getEmail(), "RESET", code);
    user.changePassword(encoder.encode(password));
    resets.findByUserIdAndUsedAtIsNull(user.getId()).forEach(ExpiringToken::consume);
  }

  /**
   * Locks the same user row used by other credential changes. Requires a correct current
   * password and a different replacement, then encodes it, increments credentialVersion, and
   * invalidates unused reset links.
   */
  public void changePassword(Long id, Change r) {
    var found = users.findById(id).orElseThrow(() -> new ApiException(401, "Please log in again."));
    var user = users.lockByEmail(found.getEmail()).orElseThrow();
    if (!encoder.matches(r.currentPassword(), user.getPasswordHash()))
      throw new ApiException(400, "Your current password is incorrect.");
    if (encoder.matches(r.password(), user.getPasswordHash()))
      throw new ApiException(400, "Choose a different new password.");
    user.changePassword(encoder.encode(r.password()));
    resets.findByUserIdAndUsedAtIsNull(id).forEach(ExpiringToken::consume);
  }
}
