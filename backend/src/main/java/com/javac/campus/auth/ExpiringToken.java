package com.javac.campus.auth;

import com.javac.campus.user.User;
import jakarta.persistence.*;
import java.time.Instant;

/**
 * Reusable JPA mapping for single-use verification and password-reset link tokens.
 * Subclasses inherit the user, hash, expiry, and consumption state; six-digit OTPs use separate storage.
 *
 * @MappedSuperclass shares fields/methods without creating an expiring_token table.
 * VerificationToken and PasswordResetToken each map these inherited columns to a separate table.
 * A null usedAt means unconsumed; it does not by itself prove that the token is unexpired.
 */
@MappedSuperclass
public abstract class ExpiringToken {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  // Database-generated identifier for this link-token record.
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  // Many link tokens can belong to one account; load the account lazily within a transaction.
  private User user;

  @Column(nullable = false, unique = true, length = 64)
  // Unique SHA-256 hash used to find the token without storing the emailed raw secret.
  private String tokenHash;

  @Column(nullable = false)
  // Instant after which the link is rejected even if it has never been used.
  private Instant expiresAt;

  // Null until consumption; a timestamp prevents the same link being accepted again.
  private Instant usedAt;

  @Column(nullable = false)
  // Issue time used for history ordering and resend cooldown checks.
  private Instant createdAt;

  /**
   * Protected no-argument constructor required by JPA to rebuild persisted entities.
   */
  protected ExpiringToken() {}

  /**
   * Initializes shared token state. The input hash must already be derived from a random raw
   * link token; usedAt stays null until successful consumption.
   */
  protected ExpiringToken(User user, String hash, Instant expiresAt) {
    this.user = user;
    this.tokenHash = hash;
    this.expiresAt = expiresAt;
    this.createdAt = Instant.now();
  }

  /**
   * A link is valid only while usedAt is null and expiresAt is strictly in the future. Token
   * lookup proves possession; this method checks the remaining lifetime and single-use state.
   */
  public boolean isValid() {
    return usedAt == null && expiresAt.isAfter(Instant.now());
  }

  /**
   * Marks this managed token with the current consumption time. The enclosing transaction
   * persists that change; keeping the record allows resend/history checks without making the
   * secret reusable.
   */
  public void consume() {
    usedAt = Instant.now();
  }

  /**
   * Returns the associated account. The LAZY relationship is normally accessed inside a service
   * transaction so JPA can load it when needed.
   */
  public User getUser() {
    return user;
  }

  /**
   * Returns when the link record was created, used for newest-token queries and resend cooldown
   * checks.
   */
  public Instant getCreatedAt() {
    return createdAt;
  }
}
