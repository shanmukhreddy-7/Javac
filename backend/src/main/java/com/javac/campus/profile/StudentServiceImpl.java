package com.javac.campus.profile;

import com.javac.campus.common.ApiException;
import com.javac.campus.profile.ProfileDtos.*;
import com.javac.campus.skill.SkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional implementation of profile editing and skill assignment for the authenticated student.
 *
 * @Transactional keeps the profile and its lazy relationships managed while reading/editing.
 * Entity methods change state, Hibernate detects those changes, and View.from returns only the
 * intended response fields.
 */
@Service
@Transactional
public class StudentServiceImpl implements StudentService {
  // Loads the managed profile associated with the supplied account ID.
  private final StudentProfileRepository profiles;
  // Resolves client-supplied IDs against existing directory entities.
  private final SkillRepository skills;

  /**
   * Constructor injection: Spring supplies the collaborators declared here, so this class can
   * delegate work without constructing its own repositories or services.
   */
  public StudentServiceImpl(StudentProfileRepository profiles, SkillRepository skills) {
    this.profiles = profiles;
    this.skills = skills;
  }

  /**
   * Finds a profile by account ID and reports a safe 404 when missing. The controller passes
   * the signed-in principal ID, not a client-selected target account.
   */
  private StudentProfile own(Long id) {
    return profiles.findByUserId(id).orElseThrow(() -> new ApiException(404, "Profile not found."));
  }

  /**
   * Read-only transaction loads the current profile and maps it to the explicit View DTO.
   * Mapping occurs before the transaction ends, so lazy user/skill relationships remain
   * accessible.
   */
  @Transactional(readOnly = true)
  public View get(Long id) {
    return View.from(own(id));
  }

  /**
   * Loads the student's managed profile, applies validated edits through its update method, and
   * returns the new safe view. JPA dirty checking writes changes at transaction commit without
   * an explicit save call.
   */
  public View update(Long id, Update r) {
    var p = own(id);
    p.update(r.fullName(), r.department(), r.studyYear(), r.bio(), r.preferredLocation());
    return View.from(p);
  }

  /**
   * Resolves all submitted IDs against persisted skills and rejects the entire change if any
   * are unknown. Replaces the managed relationship set and returns the updated view in the same
   * transaction.
   */
  public View assignSkills(Long id, Skills r) {
    // Resolve supplied IDs against the directory; never create arbitrary skills from client input.
    var selected = skills.findAllById(r.skillIds());
    if (selected.size() != r.skillIds().size())
      throw new ApiException(400, "Choose skills from the directory.");
    var p = own(id);
    p.setSkills(selected);
    return View.from(p);
  }
}
