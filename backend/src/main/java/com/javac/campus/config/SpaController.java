package com.javac.campus.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** The container serves the compiled React app; API routes are handled by Java controllers.
 *
 * @Controller returns view/forward/redirect instructions, unlike @RestController methods that
 * return JSON bodies. Explicit route mappings allow deep-link browser refreshes while leaving
 * /api processing to REST controllers.
 */
@Controller
public class SpaController {
  /**
   * Redirects the public presentation entry URL to its static index file. This uses redirect,
   * so the browser receives a new location rather than a JSON response.
   */
  @GetMapping({"/presentation", "/presentation/"})
  public String presentation() {
    return "redirect:/presentation/index.html";
  }

  /**
   * Forwards known browser routes internally to the compiled React index.html. React chooses
   * the screen after loading; Java /api handlers remain separate. Forwarding lets refreshing a
   * nested page work without a server-side HTML page for each route.
   */
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
