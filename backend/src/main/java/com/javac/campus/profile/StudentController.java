package com.javac.campus.profile;

import com.javac.campus.security.StudentPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Authenticated student profile endpoints.
 * The principal supplies the account ID so a caller cannot edit someone else by sending an ID.
 *
 * All operations target /api/students/me. The authenticated principal, rather than a body field,
 * decides whose profile is read or edited. Validation handles input shape and the service
 * handles persisted relationships.
 */
@RestController
@RequestMapping("/api/students/me")
public class StudentController {
  // Profile service abstraction; owns persistence/update rules beyond DTO validation.
  private final StudentService students;

  /**
   * Constructor injection: Spring supplies the collaborators declared here, so this class can
   * delegate work without constructing its own repositories or services.
   */
  public StudentController(StudentService students) {
    this.students = students;
  }

  /**
   * GET /api/students/me: returns the authenticated account's profile. Spring injects identity
   * from the session, so there is no editable target ID in the URL.
   */
  @GetMapping
  public ProfileDtos.View get(@AuthenticationPrincipal StudentPrincipal p) {
    return students.get(p.id());
  }

  /**
   * PATCH /api/students/me: validates editable fields and passes the session account ID to the
   * profile service. The request cannot choose another student as its target.
   */
  @PatchMapping
  public ProfileDtos.View update(
      @AuthenticationPrincipal StudentPrincipal p, @Valid @RequestBody ProfileDtos.Update r) {
    return students.update(p.id(), r);
  }

  /**
   * PUT /api/students/me/skills: validates the set of positive skill IDs and replaces the
   * signed-in student's skill selection through the service.
   */
  @PutMapping("/skills")
  public ProfileDtos.View skills(
      @AuthenticationPrincipal StudentPrincipal p, @Valid @RequestBody ProfileDtos.Skills r) {
    return students.assignSkills(p.id(), r);
  }
}
