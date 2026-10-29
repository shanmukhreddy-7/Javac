package com.javac.campus.auth;

import com.javac.campus.auth.AuthDtos.*;
import com.javac.campus.common.ApiException;
import com.javac.campus.profile.*;
import com.javac.campus.security.StudentPrincipal;
import com.javac.campus.user.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthServiceImpl implements AuthService {
  private final UserRepository users;
  private final StudentProfileRepository profiles;
  private final VerificationTokenRepository verification;
  private final PasswordResetTokenRepository resets;
  private final PasswordEncoder encoder;
  private final EmailDelivery email;
  private final List<String> domains;
  private final String frontend;
  private final String dummyHash;

  public AuthServiceImpl(
      UserRepository users,
      StudentProfileRepository profiles,
      VerificationTokenRepository verification,
      PasswordResetTokenRepository resets,
      PasswordEncoder encoder,
      EmailDelivery email,
      @Value("${app.approved-domains}") String domains,
      @Value("${app.frontend-url}") String frontend) {
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

  private String normalize(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  public List<String> approvedDomains() {
    return domains;
  }

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
    issueVerification(user);
  }

  public StudentPrincipal login(Login r) {
    var user = users.findByEmail(normalize(r.email())).orElse(null);
    boolean matches =
        encoder.matches(r.password(), user == null ? dummyHash : user.getPasswordHash());
    if (user == null || !matches) throw new ApiException(401, "Incorrect email or password.");
    if (!user.isEmailVerified())
      throw new ApiException(403, "Verify your university email before logging in.");
    if (!"ACTIVE".equals(user.getAccountStatus()))
      throw new ApiException(403, "Your account is not active. Contact the administrator.");
    return new StudentPrincipal(user.getId(), user.getEmail(), user.getCredentialVersion());
  }

  private void issueVerification(User user) {
    verification.findByUserIdAndUsedAtIsNull(user.getId()).forEach(ExpiringToken::consume);
    String raw = TokenGenerator.create();
    verification.save(
        new VerificationToken(
            user, TokenGenerator.hash(raw), Instant.now().plus(Duration.ofMinutes(30))));
    email.send(
        user.getEmail(), "Verify your university email", frontend + "/verify-email#token=" + raw);
  }

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

  public void resend(String address) {
    var user = users.lockByEmail(normalize(address)).orElse(null);
    if (user == null || user.isEmailVerified() || !"PENDING".equals(user.getAccountStatus()))
      return;
    var last = verification.findFirstByUserIdOrderByCreatedAtDesc(user.getId());
    // Generic response also covers cooldown to avoid revealing account existence.
    if (last.isPresent() && last.get().getCreatedAt().isAfter(Instant.now().minusSeconds(60)))
      return;
    issueVerification(user);
  }

  public void forgotPassword(String address) {
    var user = users.lockByEmail(normalize(address)).orElse(null);
    if (user == null || !user.isEmailVerified() || !"ACTIVE".equals(user.getAccountStatus()))
      return;
    var last = resets.findFirstByUserIdOrderByCreatedAtDesc(user.getId());
    if (last.isPresent() && last.get().getCreatedAt().isAfter(Instant.now().minusSeconds(60)))
      return;
    resets.findByUserIdAndUsedAtIsNull(user.getId()).forEach(ExpiringToken::consume);
    String raw = TokenGenerator.create();
    resets.save(
        new PasswordResetToken(user, TokenGenerator.hash(raw), Instant.now().plusSeconds(900)));
    email.send(user.getEmail(), "Reset your password", frontend + "/reset-password#token=" + raw);
  }

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
