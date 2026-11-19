package com.javac.campus.skill;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for skill-directory records seeded through database migrations.
 *
 * JpaRepository<Skill, Long> means entity type Skill and primary-key type Long. Spring creates
 * the implementation and inherited findAll/findById/findAllById/save methods; an empty interface
 * does not mean persistence is unimplemented.
 */
public interface SkillRepository extends JpaRepository<Skill, Long> {}
