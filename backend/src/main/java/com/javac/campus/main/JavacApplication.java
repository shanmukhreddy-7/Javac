package com.javac.campus.main;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Starts the Java server and scans all JAVAC packages for components, JPA entities, and repositories.
 * Business rules belong in services; this entry point only boots the application.
 *
 * This main class sits in a child package, so explicit scan settings include all sibling feature
 * packages. Built-in generated-user security and automatic session configuration are excluded
 * because the application provides its own authentication and production session setup.
 */
@org.springframework.boot.autoconfigure.domain.EntityScan("com.javac.campus")
@org.springframework.data.jpa.repository.config.EnableJpaRepositories("com.javac.campus")
@SpringBootApplication(
    scanBasePackages = "com.javac.campus",
    exclude = {
      org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
          .class,
      org.springframework.boot.autoconfigure.session.SessionAutoConfiguration.class
    })
public class JavacApplication {
  /**
   * Entry point called by the JVM. SpringApplication creates the application context, wires
   * dependencies, starts the embedded web server, and runs configured startup tasks such as
   * Flyway. args carries optional command-line configuration.
   */
  public static void main(String[] args) {
    SpringApplication.run(JavacApplication.class, args);
  }
}
