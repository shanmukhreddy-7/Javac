package com.javac.campus.config;

import com.zaxxer.hikari.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration
public class DataSourceConfig {
  @Bean
  public DataSource dataSource(Environment env) {
    var config = new HikariConfig();
    String neon = env.getProperty("DATABASE_URL_UNPOOLED", env.getProperty("DATABASE_URL", ""));
    if (!neon.isBlank()) {
      var uri = URI.create(neon);
      if (!java.util.Set.of("postgres", "postgresql").contains(uri.getScheme()))
        throw new IllegalStateException("DATABASE_URL must be a PostgreSQL URL.");
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
    config.setMaximumPoolSize(5);
    config.setMinimumIdle(0);
    config.setConnectionTimeout(15000);
    return new HikariDataSource(config);
  }
}
