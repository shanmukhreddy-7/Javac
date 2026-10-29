package com.javac.campus.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

@Configuration
@Profile("prod")
public class ProductionSettings {
  private final boolean secure;
  private final org.springframework.core.env.Environment environment;
  private final String frontend, origins, mailHost, domains;

  public ProductionSettings(
      org.springframework.core.env.Environment environment,
      @Value("${server.servlet.session.cookie.secure}") boolean secure,
      @Value("${app.frontend-url}") String frontend,
      @Value("${app.allowed-origins}") String origins,
      @Value("${spring.mail.host}") String mailHost,
      @Value("${app.approved-domains}") String domains) {
    this.environment = environment;
    this.secure = secure;
    this.frontend = frontend;
    this.origins = origins;
    this.mailHost = mailHost;
    this.domains = domains;
  }

  @PostConstruct
  void validate() {
    if (java.util.Arrays.asList(environment.getActiveProfiles()).contains("dev")
        || !secure
        || !frontend.startsWith("https://")
        || origins.contains("*")
        || domains.isBlank()
        || mailHost.equals("localhost"))
      throw new IllegalStateException(
          "Production requires secure cookies, an HTTPS frontend, explicit allowed origins,"
              + " approved email domains, and an SMTP host.");
  }
}
