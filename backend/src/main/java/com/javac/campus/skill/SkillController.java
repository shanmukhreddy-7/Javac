package com.javac.campus.skill;

import java.util.List;
import org.springframework.web.bind.annotation.*;

/**
 * Public endpoint that returns the skill directory used by profile selection forms.
 *
 * This read-only HTTP adapter exposes the shared directory needed during profile editing. Spring
 * serializes entity getters into JSON; there are no create/update/delete endpoints here.
 */
@RestController
@RequestMapping("/api/skills")
public class SkillController {
  // Spring-generated directory repository used by the read-only endpoint.
  private final SkillRepository skills;

  /**
   * Constructor injection: Spring supplies the collaborators declared here, so this class can
   * delegate work without constructing its own repositories or services.
   */
  public SkillController(SkillRepository skills) {
    this.skills = skills;
  }

  /**
   * GET /api/skills: returns the persisted skill directory. This endpoint is public and reads
   * the seed entries supplied by Flyway; it does not create skills from browser input.
   */
  @GetMapping
  public List<Skill> directory() {
    return skills.findAll();
  }
}
