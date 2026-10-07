package ru.nexus;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import ru.nexus.config.CatalogueData;
import ru.nexus.config.DemoData;
import ru.nexus.repository.*;
import ru.nexus.service.NexusService;

@SpringBootTest(
  properties = {
    "spring.datasource.url=jdbc:h2:mem:demo-startup;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "nexus.demo=true",
  }
)
@TestExecutionListeners(
  listeners = DependencyInjectionTestExecutionListener.class,
  inheritListeners = false
)
class DemoStartupTest {

  @Autowired
  UserAccountRepository users;

  @Autowired
  InterestRepository interests;

  @Autowired
  PreferenceRepository preferences;

  @Autowired
  NexusService service;

  @Autowired
  DemoData demo;

  @Autowired
  CatalogueData catalogue;

  @Autowired
  jakarta.persistence.EntityManagerFactory entityManagerFactory;

  @Test
  void allSixDemoProfilesStartAndReseedingIsSafe() {
    assertEquals(6, users.count());
    assertEquals(12, interests.count());
    assertEquals(6, preferences.count());
    assertTrue(entityManagerFactory.getMetamodel().getEntities().size() >= 10);
    for (var user : users.findAll())
      assertTrue(service.profileComplete(user.id), user.email);
    demo.run();
    catalogue.run();
    assertEquals(6, users.count());
    assertEquals(12, interests.count());
    assertEquals(
      5,
      service
        .recommend(users.findByEmail("demo@nexus.local").orElseThrow().id, 20)
        .size()
    );
  }
}
