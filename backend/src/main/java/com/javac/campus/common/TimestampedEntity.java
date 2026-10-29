package com.javac.campus.common;

import jakarta.persistence.*;
import java.time.Instant;

@MappedSuperclass
public abstract class TimestampedEntity {
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  @PrePersist
  protected void created() {
    createdAt = Instant.now();
    updatedAt = createdAt;
  }

  @PreUpdate
  protected void updated() {
    updatedAt = Instant.now();
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
