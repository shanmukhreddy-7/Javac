package com.javac.campus.skill;

import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/skills")
public class SkillController {
  private final SkillRepository skills;

  public SkillController(SkillRepository skills) {
    this.skills = skills;
  }

  @GetMapping
  public List<Skill> directory() {
    return skills.findAll();
  }
}
