package ru.nexus;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import ru.nexus.config.CatalogueData;
import ru.nexus.dto.Requests.Credentials;
import ru.nexus.repository.*;
import ru.nexus.service.NexusService;

@SpringBootTest(
  properties = {
    "spring.datasource.url=jdbc:h2:mem:legacy;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "nexus.demo=false",
  }
)
@TestExecutionListeners(
  listeners = DependencyInjectionTestExecutionListener.class,
  inheritListeners = false
)
class LegacyMigrationTest {

  @Autowired
  JdbcTemplate jdbc;

  @Autowired
  NexusService service;

  @Autowired
  UserAccountRepository users;

  @Autowired
  PreferenceRepository preferences;

  @Autowired
  UserInterestRepository selected;

  @Autowired
  CatalogueData catalogue;

  @Test
  void importsLegacyInterestsAndPreferencesWithoutLosingAccounts() {
    service.authenticate(
      new Credentials("legacy@example.com", "Password123!"),
      true
    );
    long id = users.findByEmail("legacy@example.com").orElseThrow().id;
    preferences.deleteAll();
    jdbc.execute("alter table users add column min_age integer default 25");
    jdbc.execute("alter table users add column max_age integer default 35");
    jdbc.execute(
      "create table user_account_interests(user_account_id bigint, interests varchar(255))"
    );
    jdbc.update(
      "insert into user_account_interests values (?,?)",
      id,
      "Музыка"
    );
    catalogue.run();
    catalogue.run();
    assertEquals(1, selected.findByUserId(id).size());
    assertEquals(
      Map.of("minAge", 25, "maxAge", 35),
      service.me(id).get("preferences")
    );
    assertTrue(users.findByEmail("legacy@example.com").isPresent());
  }
}
