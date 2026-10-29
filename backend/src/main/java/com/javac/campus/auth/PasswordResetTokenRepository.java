package com.javac.campus.auth;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<PasswordResetToken> findByTokenHash(String hash);

  Optional<PasswordResetToken> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

  List<PasswordResetToken> findByUserIdAndUsedAtIsNull(Long userId);
}
