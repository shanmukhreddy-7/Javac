package com.javac.campus.auth;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;

/**
 * Spring Data queries for reset-link tokens; pessimistic lookup serializes token consumption.
 *
 * Spring Data implements these method-name queries. Locked hash lookup protects consumption
 * while newest/unused queries support cooldown and replacement; the authentication service
 * supplies transaction boundaries.
 */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
  /**
   * Finds a password-reset link by its stored hash and locks it for the current transaction.
   * Two simultaneous reset requests cannot both consume the same valid token.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<PasswordResetToken> findByTokenHash(String hash);

  /**
   * Returns the most recently created reset link for cooldown decisions. Descending creation
   * time plus First means only the newest row is needed.
   */
  Optional<PasswordResetToken> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

  /**
   * Lists unused reset links for an account so a new recovery request or successful password
   * change can invalidate the older recovery paths.
   */
  List<PasswordResetToken> findByUserIdAndUsedAtIsNull(Long userId);
}
