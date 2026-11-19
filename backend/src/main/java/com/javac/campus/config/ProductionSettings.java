package com.javac.campus.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

/**
 * Fails production startup when required database, HTTPS, cookie, origin, or mail settings are missing.
 *
 * @Profile limits these guards to production. @Value and Environment supply deployment settings;
 * @PostConstruct checks them after construction. This class validates configuration and contains
 * no HTTP endpoints.
 */
@Configuration
@Profile("prod")
public class ProductionSettings {
  // Resolved HTTPS-only cookie flag required for production.
  private final boolean secure;
  // Reads active profiles and deployment database/provider properties.
  private final org.springframework.core.env.Environment environment;
  // Injected public origin, mail, and university-domain settings checked before startup completes.
  private final String frontend, origins, mailHost, domains;

  /**
   * Constructor injection: Spring supplies the collaborators declared here, so this class can
   * delegate work without constructing its own repositories or services.
   */
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

  // Validate only after Spring has injected all configuration values.
  /**
   * Runs after dependency injection only in the prod profile. Rejects dev/prod overlap, missing
   * PostgreSQL, insecure cookies/frontend, wildcard origins, missing university domains, and
   * absent mail/ownership-provider configuration. A failed guard stops startup before requests
   * are accepted.
   */
  @PostConstruct
  void validate() {
    if (java.util.Arrays.asList(environment.getActiveProfiles()).contains("dev")
        || (environment.getProperty("DATABASE_URL", "").isBlank()
            && environment.getProperty("DATABASE_URL_UNPOOLED", "").isBlank()
            && !environment.getProperty("spring.datasource.url", "").startsWith("jdbc:postgresql:"))
        || !secure
        || !frontend.startsWith("https://")
        || origins.contains("*")
        || domains.isBlank()
        || (mailHost.equals("localhost")
            && environment.getProperty("app.neon-auth-url", "").isBlank()))
      throw new IllegalStateException(
          "Production requires secure cookies, an HTTPS frontend, explicit allowed origins,"
              + " approved email domains, PostgreSQL, and Neon Auth or an SMTP host.");
  }
}
