package com.javac.campus.auth;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;

/**
 * Spring Data queries for verification links, including locked lookup and resend history.
 *
 * Spring Data implements these method-name queries. Optional represents one possible record and
 * List represents multiple records. Locking lookup requires an active transaction, supplied by
 * the calling authentication service.
 */
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {
  /**
   * Looks up the hash of an emailed verification token and locks the row with
   * PESSIMISTIC_WRITE. The service checks expiry and usedAt while the row is protected against
   * concurrent consumption.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<VerificationToken> findByTokenHash(String hash);

  /**
   * Finds the newest verification-link record for one account. Its creation time supplies the
   * resend cooldown; Optional is empty before any link has been issued.
   */
  Optional<VerificationToken> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

  /**
   * Returns unconsumed verification links belonging to the user, including any expired ones.
   * The service marks them consumed when issuing a replacement link.
   */
  List<VerificationToken> findByUserIdAndUsedAtIsNull(Long userId);
}
