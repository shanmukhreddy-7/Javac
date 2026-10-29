package com.javac.campus.auth;

import com.javac.campus.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "email_verification_tokens")
public class VerificationToken extends ExpiringToken {
  protected VerificationToken() {}

  public VerificationToken(User user, String hash, Instant expiresAt) {
    super(user, hash, expiresAt);
  }
}
