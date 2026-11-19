package com.javac.campus.user;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

/**
 * Account persistence and normalized-email lookup; locked lookup serializes credential changes.
 *
 * Derived find/exists methods are implemented by Spring Data at startup. The explicit @Query is
 * JPQL using Java entity/property names, not raw SQL table names; @Lock changes its
 * transactional locking behavior.
 */
public interface UserRepository extends JpaRepository<User, Long> {
  /**
   * Spring Data derives a SELECT from this name: look up users by email and return
   * Optional.empty() when absent. Callers normalize email before querying.
   */
  Optional<User> findByEmail(String email);

  /**
   * Derived existence query used before registration to give a helpful duplicate-account error.
   * The database UNIQUE constraint remains the final authority under concurrent registrations.
   */
  boolean existsByEmail(String email);

  /**
   * JPQL selects the User entity by its named email parameter. PESSIMISTIC_WRITE locks the
   * matching database row until transaction completion, serializing password changes and other
   * account-sensitive operations.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where u.email=:email")
  Optional<User> lockByEmail(String email);
}
