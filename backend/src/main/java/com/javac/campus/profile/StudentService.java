package com.javac.campus.profile;

import com.javac.campus.profile.ProfileDtos.*;

public interface StudentService {
  View get(Long userId);

  View update(Long userId, Update request);

  View assignSkills(Long userId, Skills request);
}
