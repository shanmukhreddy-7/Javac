package com.javac.campus.config;

import com.zaxxer.hikari.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

/**
 * Builds the shared JDBC connection pool from Neon environment settings or the local datasource URL.
 * Both JPA repositories and JdbcTemplate use the resulting DataSource.
 *
 * DataSource is the connection-provider interface; HikariDataSource implements it with a
 * reusable pool. SQL is executed by repositories/JdbcTemplate using borrowed connections.
 * Creating a pool does not itself define the database schema: Flyway migrations do that.
 */
@Configuration
public class DataSourceConfig {
  /**
   * Creates the shared Hikari connection pool used by Hibernate/JPA, JdbcTemplate, Flyway, and
   * JDBC sessions. Production URL credentials are decoded separately and TLS verifies the
   * server certificate; otherwise Spring's configured local JDBC URL is used. Bounds
   * connections and wait time to avoid an unbounded pool.
   */
  @Bean
  public DataSource dataSource(Environment env) {
    var config = new HikariConfig();
    // Prefer the direct production URL when supplied; otherwise use the configured local JDBC URL.
    String neon = env.getProperty("DATABASE_URL_UNPOOLED", env.getProperty("DATABASE_URL", ""));
    if (!neon.isBlank()) {
      var uri = URI.create(neon);
      if (!java.util.Set.of("postgres", "postgresql").contains(uri.getScheme()))
        throw new IllegalStateException("DATABASE_URL must be a PostgreSQL URL.");
      // Decode URL credentials separately from the JDBC URL so they are supplied through pool
      // settings.
      var credentials = uri.getRawUserInfo().split(":", 2);
      config.setJdbcUrl(
          "jdbc:postgresql://"
              + uri.getHost()
              + (uri.getPort() > 0 ? ":" + uri.getPort() : "")
              + uri.getPath()
              + "?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory");
      config.setUsername(URLDecoder.decode(credentials[0], StandardCharsets.UTF_8));
      config.setPassword(URLDecoder.decode(credentials[1], StandardCharsets.UTF_8));
    } else {
      config.setJdbcUrl(env.getRequiredProperty("spring.datasource.url"));
      config.setUsername(env.getProperty("spring.datasource.username", "sa"));
      config.setPassword(env.getProperty("spring.datasource.password", ""));
    }
    config.setSchema(env.getProperty("DB_SCHEMA", "public"));
    // Keep the connection pool bounded; local and production stores use the same DataSource
    // contract.
    config.setMaximumPoolSize(5);
    config.setMinimumIdle(0);
    config.setConnectionTimeout(15000);
    return new HikariDataSource(config);
  }
}
