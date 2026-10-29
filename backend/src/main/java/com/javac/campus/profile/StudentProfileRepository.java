package com.javac.campus.profile;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {
  Optional<StudentProfile> findByUserId(Long userId);

  boolean existsByRegistrationNumber(String registrationNumber);
}
