package com.javac.campus.profile;

import com.javac.campus.profile.ProfileDtos.*;

/**
 * Profile service contract separating HTTP endpoints from persistence and update rules.
 *
 * This interface defines a profile-service contract. StudentController depends on the
 * abstraction and Spring injects StudentServiceImpl; repositories remain an implementation
 * detail.
 */
public interface StudentService {
  /**
   * Returns the safe profile view for an account ID. HTTP callers should supply their
   * authenticated principal ID.
   */
  View get(Long userId);

  /**
   * Applies validated editable profile fields and returns the resulting view.
   * Identity/credential changes are handled by other workflows.
   */
  View update(Long userId, Update request);

  /**
   * Replaces the account's chosen skills using existing directory IDs, rejecting unknown
   * entries rather than creating new ones.
   */
  View assignSkills(Long userId, Skills request);
}
