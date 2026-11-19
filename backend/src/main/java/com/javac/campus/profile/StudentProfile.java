package com.javac.campus.profile;

import com.javac.campus.skill.Skill;
import com.javac.campus.user.User;
import jakarta.persistence.*;
import java.util.*;

/**
 * JPA student details linked to one user and a set of directory skills.
 * The join table stores skill associations rather than duplicating the skill records.
 *
 * The account relationship is one-to-one; the profile has its own ID, while user_id identifies
 * its owner. LAZY defers loading User until needed. student_skills links many profiles to many
 * shared skills; its student_id refers to the profile ID, not the user ID.
 */
@Entity
@Table(name = "student_profiles")
public class StudentProfile {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  // Profile primary key; student_skills.student_id points here, not to users.id.
  private Long id;

  @OneToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  // Owning account, linked through unique student_profiles.user_id.
  private User user;

  @Column(nullable = false, length = 80)
  // Display name, separate from immutable login identity.
  private String fullName;

  @Column(nullable = false, unique = true, length = 40)
  // University registration identifier; normalized and unique across profiles.
  private String registrationNumber;

  @Column(nullable = false, length = 80)
  // Student-entered department text validated by the profile DTO.
  private String department;

  @Column(nullable = false)
  // Current academic year, constrained to 1 through 6.
  private int studyYear;

  @Column(nullable = false, length = 500)
  // Optional student biography; an empty string represents an unfinished field.
  private String bio = "";

  @Column(length = 500)
  // Reserved nullable avatar URL column; the current update/view DTOs do not expose an avatar
  // workflow.
  private String avatarUrl;

  @Column(nullable = false, length = 100)
  // Student-entered preferred campus location; not tracked GPS coordinates.
  private String preferredLocation = "";

  // Multiple students may share directory skills; student_skills stores those relationships.
  @ManyToMany
  @JoinTable(
      name = "student_skills",
      joinColumns = @JoinColumn(name = "student_id"),
      inverseJoinColumns = @JoinColumn(name = "skill_id"))
  // Distinct directory entities connected through student_skills rather than copied names.
  private Set<Skill> skills = new LinkedHashSet<>();

  /**
   * Protected no-argument constructor required by JPA to rebuild persisted entities.
   */
  protected StudentProfile() {}

  /**
   * Connects a new profile to its persisted User and registration details. Optional
   * biography/location and the skills collection retain their empty defaults.
   */
  public StudentProfile(
      User user, String fullName, String registrationNumber, String department, int studyYear) {
    this.user = user;
    this.fullName = fullName;
    this.registrationNumber = registrationNumber;
    this.department = department;
    this.studyYear = studyYear;
  }

  /**
   * Updates only editable profile fields and trims text before assigning it. Account identity,
   * registration number, credentials, and ownership are intentionally outside this operation;
   * the controller DTO validates size/year first.
   */
  public void update(String name, String department, int year, String bio, String location) {
    this.fullName = name.trim();
    this.department = department.trim();
    this.studyYear = year;
    this.bio = bio.trim();
    this.preferredLocation = location.trim();
  }

  /**
   * Replaces membership by clearing and refilling the managed collection. JPA observes the
   * collection changes and updates student_skills on flush; selected Skill entities must
   * already come from the directory.
   */
  public void setSkills(Collection<Skill> skills) {
    this.skills.clear();
    this.skills.addAll(skills);
  }

  public User getUser() {
    return user;
  }

  /**
   * Returns the student display name.
   */
  public String getFullName() {
    return fullName;
  }

  /**
   * Returns the stored university registration identifier.
   */
  public String getRegistrationNumber() {
    return registrationNumber;
  }

  /**
   * Returns the student department.
   */
  public String getDepartment() {
    return department;
  }

  /**
   * Returns the academic year.
   */
  public int getStudyYear() {
    return studyYear;
  }

  /**
   * Returns the optional biography text.
   */
  public String getBio() {
    return bio;
  }

  /**
   * Returns the declared preferred campus location, not a live location.
   */
  public String getPreferredLocation() {
    return preferredLocation;
  }

  /**
   * Returns the managed skill set; this is not a defensive copy, so application updates should
   * use setSkills.
   */
  public Set<Skill> getSkills() {
    return skills;
  }
}
