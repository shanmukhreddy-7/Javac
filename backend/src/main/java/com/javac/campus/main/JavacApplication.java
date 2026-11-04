package com.javac.campus.main;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

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
  public static void main(String[] args) {
    SpringApplication.run(JavacApplication.class, args);
  }
}
