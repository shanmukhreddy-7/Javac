package com.javac.campus.profile;

import com.javac.campus.common.ApiException;
import com.javac.campus.profile.ProfileDtos.*;
import com.javac.campus.skill.SkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StudentServiceImpl implements StudentService {
  private final StudentProfileRepository profiles;
  private final SkillRepository skills;

  public StudentServiceImpl(StudentProfileRepository profiles, SkillRepository skills) {
    this.profiles = profiles;
    this.skills = skills;
  }

  private StudentProfile own(Long id) {
    return profiles.findByUserId(id).orElseThrow(() -> new ApiException(404, "Profile not found."));
  }

  @Transactional(readOnly = true)
  public View get(Long id) {
    return View.from(own(id));
  }

  public View update(Long id, Update r) {
    var p = own(id);
    p.update(r.fullName(), r.department(), r.studyYear(), r.bio(), r.preferredLocation());
    return View.from(p);
  }

  public View assignSkills(Long id, Skills r) {
    var selected = skills.findAllById(r.skillIds());
    if (selected.size() != r.skillIds().size())
      throw new ApiException(400, "Choose skills from the directory.");
    var p = own(id);
    p.setSkills(selected);
    return View.from(p);
  }
}
