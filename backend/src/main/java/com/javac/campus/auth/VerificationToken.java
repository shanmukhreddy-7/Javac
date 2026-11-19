package com.javac.campus.auth;

import com.javac.campus.user.User;
import jakarta.persistence.*;
import java.time.Instant;

/**
 * Email-verification link token entity that inherits expiry and single-use rules from ExpiringToken.
 *
 * Inheritance supplies all mapped fields and single-use methods. This subclass selects
 * email_verification_tokens; its constructor passes ownership, a hash, and expiry to the common
 * base. Only email-verification link mode uses this table.
 */
@Entity
@Table(name = "email_verification_tokens")
public class VerificationToken extends ExpiringToken {
  /**
   * Protected no-argument constructor required by JPA to rebuild persisted entities.
   */
  protected VerificationToken() {}

  /**
   * Initializes the inherited user, hash, creation time, and expiry for a new email-
   * verification link record.
   */
  public VerificationToken(User user, String hash, Instant expiresAt) {
    super(user, hash, expiresAt);
  }
}
