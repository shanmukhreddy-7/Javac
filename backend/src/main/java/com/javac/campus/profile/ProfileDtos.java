package com.javac.campus.profile;

import com.javac.campus.skill.Skill;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class ProfileDtos {
  private ProfileDtos() {}

  public record Update(
      @NotBlank @Size(min = 2, max = 80) String fullName,
      @NotBlank @Size(max = 80) String department,
      @Min(1) @Max(6) int studyYear,
      @NotNull @Size(max = 500) String bio,
      @NotNull @Size(max = 100) String preferredLocation) {}

  public record Skills(@NotNull @Size(max = 20) Set<@NotNull @Positive Long> skillIds) {}

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
      int completion) {
    public static View from(StudentProfile p) {
      var u = p.getUser();
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
          completion);
    }
  }
}
