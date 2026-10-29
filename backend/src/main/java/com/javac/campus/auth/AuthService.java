package com.javac.campus.auth;

import com.javac.campus.auth.AuthDtos.*;
import com.javac.campus.security.StudentPrincipal;
import java.util.List;

public interface AuthService {
  void register(Register request);

  StudentPrincipal login(Login request);

  void verify(String token);

  void resend(String email);

  void forgotPassword(String email);

  void resetPassword(Reset request);

  void changePassword(Long id, Change request);

  List<String> approvedDomains();
}
