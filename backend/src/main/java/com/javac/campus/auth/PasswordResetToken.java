package com.javac.campus.auth;

import com.javac.campus.user.User;
import jakarta.persistence.*;
import java.time.Instant;

/**
 * Password-reset link token entity that inherits expiry and single-use rules from ExpiringToken.
 *
 * Inheritance supplies all mapped fields and single-use methods. This subclass selects
 * password_reset_tokens, keeping recovery links separate from verification links and six-digit
 * account OTP records.
 */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken extends ExpiringToken {
  /**
   * Protected no-argument constructor required by JPA to rebuild persisted entities.
   */
  protected PasswordResetToken() {}

  /**
   * Initializes the inherited user, hash, creation time, and expiry for a new password-reset
   * link record.
   */
  public PasswordResetToken(User user, String hash, Instant expiresAt) {
    super(user, hash, expiresAt);
  }
}
