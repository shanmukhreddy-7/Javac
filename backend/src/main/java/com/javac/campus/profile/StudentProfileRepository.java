package com.javac.campus.profile;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data persistence for profiles, including user lookup and unique registration-number checks.
 *
 * Spring Data reads method names to derive entity queries. Optional expresses a possibly absent
 * single result. Inherited JpaRepository methods supply saves and ID-based retrieval without
 * handwritten SQL.
 */
public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {
  /**
   * Derived query follows StudentProfile.user.id, rather than the profile table's own ID. It
   * locates the profile associated with a logged-in account.
   */
  Optional<StudentProfile> findByUserId(Long userId);

  /**
   * Checks whether a registration number is already present. The service normalizes it before
   * calling; the unique database column also prevents concurrent duplicate inserts.
   */
  boolean existsByRegistrationNumber(String registrationNumber);
}
