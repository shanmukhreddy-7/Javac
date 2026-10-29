package com.javac.campus.auth;

import com.javac.campus.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@MappedSuperclass
public abstract class ExpiringToken {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private User user;

  @Column(nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Column(nullable = false)
  private Instant expiresAt;

  private Instant usedAt;

  @Column(nullable = false)
  private Instant createdAt;

  protected ExpiringToken() {}

  protected ExpiringToken(User user, String hash, Instant expiresAt) {
    this.user = user;
    this.tokenHash = hash;
    this.expiresAt = expiresAt;
    this.createdAt = Instant.now();
  }

  public boolean isValid() {
    return usedAt == null && expiresAt.isAfter(Instant.now());
  }

  public void consume() {
    usedAt = Instant.now();
  }

  public User getUser() {
    return user;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
