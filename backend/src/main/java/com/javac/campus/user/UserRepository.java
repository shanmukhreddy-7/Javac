package com.javac.campus.user;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByEmail(String email);

  boolean existsByEmail(String email);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where u.email=:email")
  Optional<User> lockByEmail(String email);
}
