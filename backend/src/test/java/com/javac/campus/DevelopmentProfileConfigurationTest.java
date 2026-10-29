package com.javac.campus;

import static org.assertj.core.api.Assertions.assertThat;

import com.javac.campus.auth.DevelopmentMailbox;
import com.javac.campus.auth.EmailDelivery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

/** A properties-file-style environment key must activate local email previews. */
@SpringBootTest(
    properties = {
      "SPRING_PROFILES_ACTIVE=dev",
      "spring.datasource.url=jdbc:h2:mem:javac_dev_profile;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.properties.hibernate.type.preferred_instant_jdbc_type=TIMESTAMP"
    })
class DevelopmentProfileConfigurationTest {
  @Autowired Environment environment;
  @Autowired EmailDelivery delivery;

  @Test
  void dotenvStyleProfileSelectsDevelopmentMailbox() {
    assertThat(environment.getActiveProfiles()).contains("dev");
    assertThat(delivery).isInstanceOf(DevelopmentMailbox.class);
  }
}
