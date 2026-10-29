package com.javac.campus.profile;

import com.javac.campus.security.StudentPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/students/me")
public class StudentController {
  private final StudentService students;

  public StudentController(StudentService students) {
    this.students = students;
  }

  @GetMapping
  public ProfileDtos.View get(@AuthenticationPrincipal StudentPrincipal p) {
    return students.get(p.id());
  }

  @PatchMapping
  public ProfileDtos.View update(
      @AuthenticationPrincipal StudentPrincipal p, @Valid @RequestBody ProfileDtos.Update r) {
    return students.update(p.id(), r);
  }

  @PutMapping("/skills")
  public ProfileDtos.View skills(
      @AuthenticationPrincipal StudentPrincipal p, @Valid @RequestBody ProfileDtos.Skills r) {
    return students.assignSkills(p.id(), r);
  }
}
