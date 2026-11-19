package com.javac.campus.common;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Mapped superclass that supplies creation and update timestamps through JPA lifecycle callbacks.
 * It contributes columns to subclasses rather than creating its own database table.
 *
 * JPA lifecycle callbacks initialize timestamps for mapped subclasses such as User.
 * @Column(updatable=false) keeps creation time immutable in ORM updates. JDBC marketplace/wallet
 * code manages its own timestamp columns explicitly.
 */
@MappedSuperclass
public abstract class TimestampedEntity {
  @Column(nullable = false, updatable = false)
  // First persistence time; ORM updates cannot overwrite this column.
  private Instant createdAt;

  @Column(nullable = false)
  // Most recent JPA-managed insert/update time.
  private Instant updatedAt;

  /**
   * JPA invokes @PrePersist before the first insert. Initializes createdAt and updatedAt to the
   * same Instant; inherited fields live in the subclass's table.
   */
  @PrePersist
  protected void created() {
    createdAt = Instant.now();
    updatedAt = createdAt;
  }

  /**
   * JPA invokes @PreUpdate before updating a managed entity. Advances updatedAt while createdAt
   * remains unchanged. This callback does not run for raw JdbcTemplate updates, which supply
   * their own timestamps.
   */
  @PreUpdate
  protected void updated() {
    updatedAt = Instant.now();
  }

  /**
   * Exposes creation time as an immutable Instant for safe response mapping and account
   * history.
   */
  public Instant getCreatedAt() {
    return createdAt;
  }
}
