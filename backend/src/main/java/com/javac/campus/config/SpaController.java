package com.javac.campus.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** The container serves the compiled React app; API routes are handled by Java controllers. */
@Controller
public class SpaController {
  @GetMapping({
    "/",
    "/login",
    "/register",
    "/verify-email",
    "/forgot-password",
    "/reset-password",
    "/dashboard",
    "/profile",
    "/settings",
    "/requests",
    "/jobs",
    "/my-jobs",
    "/jobs/new",
    "/jobs/{id}",
    "/earnings",
    "/notifications",
    "/admin",
    "/students/{id}"
  })
  public String index() {
    return "forward:/index.html";
  }
}
