package com.javac.campus.profile;

import com.javac.campus.skill.Skill;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

/**
 * Validated profile inputs and an explicit response view.
 * Mapping to a view keeps password hashes and internal entity state out of profile responses.
 *
 * The Update and Skills records accept only editable data. View is an explicit response
 * projection so Hibernate relationships and credential fields are not accidentally serialized.
 */
public final class ProfileDtos {
  /**
   * Private constructor prevents instantiation of this holder of static helpers or DTO types.
   */
  private ProfileDtos() {}

  /**
   * Editable profile fields only. Registration number, email, role, and credentials cannot be
   * changed through this DTO.
   */
  public record Update(
      @NotBlank @Size(min = 2, max = 80) String fullName,
      @NotBlank @Size(max = 80) String department,
      @Min(1) @Max(6) int studyYear,
      @NotNull @Size(max = 500) String bio,
      @NotNull @Size(max = 100) String preferredLocation) {}

  /**
   * A bounded set of positive directory IDs. A set removes duplicate IDs; service lookup
   * rejects IDs absent from the directory.
   */
  public record Skills(@NotNull @Size(max = 20) Set<@NotNull @Positive Long> skillIds) {}

  /**
   * Response-only projection of account/profile data and skill choices. completion is an
   * interface indicator, not an identity-verification or reputation score.
   */
  public record View(
      Long id,
      String fullName,
      String email,
      String registrationNumber,
      String department,
      int studyYear,
      String bio,
      String preferredLocation,
      boolean emailVerified,
      String accountStatus,
      Instant createdAt,
      List<Skill> skills,
      int completion,
      String role) {
  /**
   * Creates a response snapshot from StudentProfile and User without serializing the entities
   * wholesale. Computes a UI completion indicator from optional profile fields, sorts skills by
   * name, and includes account state without exposing passwordHash or credentialVersion.
   */
    public static View from(StudentProfile p) {
      var u = p.getUser();
      // Profile-completion indicator for the interface, not a trust score or identity check.
      int completion = 60;
      if (!p.getBio().isBlank()) completion += 15;
      if (!p.getPreferredLocation().isBlank()) completion += 10;
      if (!p.getSkills().isEmpty()) completion += 15;
      return new View(
          u.getId(),
          p.getFullName(),
          u.getEmail(),
          p.getRegistrationNumber(),
          p.getDepartment(),
          p.getStudyYear(),
          p.getBio(),
          p.getPreferredLocation(),
          u.isEmailVerified(),
          u.getAccountStatus(),
          u.getCreatedAt(),
          p.getSkills().stream().sorted(Comparator.comparing(Skill::getName)).toList(),
          completion,
          u.getRole());
    }
  }
}
