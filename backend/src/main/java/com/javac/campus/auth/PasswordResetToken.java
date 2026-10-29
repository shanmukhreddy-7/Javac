package com.javac.campus.auth;

import com.javac.campus.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken extends ExpiringToken {
  protected PasswordResetToken() {}

  public PasswordResetToken(User user, String hash, Instant expiresAt) {
    super(user, hash, expiresAt);
  }
}
