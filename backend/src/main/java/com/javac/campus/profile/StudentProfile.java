package com.javac.campus.profile;

import com.javac.campus.skill.Skill;
import com.javac.campus.user.User;
import jakarta.persistence.*;
import java.util.*;

@Entity
@Table(name = "student_profiles")
public class StudentProfile {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private User user;

  @Column(nullable = false, length = 80)
  private String fullName;

  @Column(nullable = false, unique = true, length = 40)
  private String registrationNumber;

  @Column(nullable = false, length = 80)
  private String department;

  @Column(nullable = false)
  private int studyYear;

  @Column(nullable = false, length = 500)
  private String bio = "";

  @Column(length = 500)
  private String avatarUrl;

  @Column(nullable = false, length = 100)
  private String preferredLocation = "";

  @ManyToMany
  @JoinTable(
      name = "student_skills",
      joinColumns = @JoinColumn(name = "student_id"),
      inverseJoinColumns = @JoinColumn(name = "skill_id"))
  private Set<Skill> skills = new LinkedHashSet<>();

  protected StudentProfile() {}

  public StudentProfile(
      User user, String fullName, String registrationNumber, String department, int studyYear) {
    this.user = user;
    this.fullName = fullName;
    this.registrationNumber = registrationNumber;
    this.department = department;
    this.studyYear = studyYear;
  }

  public void update(String name, String department, int year, String bio, String location) {
    this.fullName = name.trim();
    this.department = department.trim();
    this.studyYear = year;
    this.bio = bio.trim();
    this.preferredLocation = location.trim();
  }

  public void setSkills(Collection<Skill> skills) {
    this.skills.clear();
    this.skills.addAll(skills);
  }

  public User getUser() {
    return user;
  }

  public String getFullName() {
    return fullName;
  }

  public String getRegistrationNumber() {
    return registrationNumber;
  }

  public String getDepartment() {
    return department;
  }

  public int getStudyYear() {
    return studyYear;
  }

  public String getBio() {
    return bio;
  }

  public String getPreferredLocation() {
    return preferredLocation;
  }

  public Set<Skill> getSkills() {
    return skills;
  }
}
