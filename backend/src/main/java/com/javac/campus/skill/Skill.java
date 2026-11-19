package com.javac.campus.skill;

import jakarta.persistence.*;

/**
 * Read-only directory entry that students can associate with their profiles.
 *
 * The directory is seeded by SQL migration rather than created by these entities. JPA uses the
 * protected empty constructor to load records. No public mutation methods are exposed by this
 * class.
 */
@Entity
@Table(name = "skills")
public class Skill {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  // Generated directory ID used by profile skill selections and the join table.
  private Long id;

  @Column(nullable = false)
  // Human-readable skill name supplied by seed data.
  private String name;

  @Column(nullable = false)
  // Directory grouping such as Programming or Tutoring.
  private String category;

  /**
   * Protected no-argument constructor required by JPA to rebuild persisted entities.
   */
  protected Skill() {}

  /**
   * Returns the database-generated record ID.
   */
  public Long getId() {
    return id;
  }

  /**
   * Returns the skill directory display name.
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the skill directory category.
   */
  public String getCategory() {
    return category;
  }
}
