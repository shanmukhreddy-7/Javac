package com.javac.campus.auth;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<VerificationToken> findByTokenHash(String hash);

  Optional<VerificationToken> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

  List<VerificationToken> findByUserIdAndUsedAtIsNull(Long userId);
}
