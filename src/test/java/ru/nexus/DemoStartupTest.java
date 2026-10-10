package ru.nexus;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import ru.nexus.config.CatalogueData;
import ru.nexus.config.DemoData;
import ru.nexus.dto.Requests.*;
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
  ru.nexus.service.DataExportService dataExport;

  @Autowired
  DemoData demo;

  @Autowired
  CatalogueData catalogue;

  @Autowired
  jakarta.persistence.EntityManagerFactory entityManagerFactory;

  @Test
  void allFourteenDemoProfilesStartAndReseedingPreservesEditedProfiles() {
    assertEquals(14, users.count());
    var export = dataExport.export(
      users.findByEmail("demo@nexus.local").orElseThrow().id
    );
    assertEquals(6, ((java.util.List<?>) export.get("photos")).size());
    assertEquals(Interests.ALL.size(), interests.count());
    assertEquals(14, preferences.count());
    assertTrue(entityManagerFactory.getMetamodel().getEntities().size() >= 10);
    for (var user : users.findAll()) {
      assertTrue(service.profileComplete(user.id), user.email);
      assertTrue(user.avatarKey.matches("demo-(0[1-9]|1[0-4])"), user.email);
      assertFalse(service.profile(user.id, false).containsKey("avatarUrl"));
      assertEquals(6, service.profile(user.id, true).get("photoCount"));
      assertTrue(
        service
          .profile(user.id, true)
          .get("avatarUrl")
          .toString()
          .startsWith("/api/v1/photos/")
      );
    }
    assertEquals(
      14,
      users
        .findAll()
        .stream()
        .map(u -> u.avatarKey)
        .distinct()
        .count()
    );
    demo.run();
    catalogue.run();
    assertEquals(14, users.count());
    assertEquals(Interests.ALL.size(), interests.count());
    var band = service.recommend(users.findByEmail("demo@nexus.local").orElseThrow().id, 20);
    assertFalse(band.isEmpty());
    assertTrue(band.size() <= 13);
    assertEquals(1, band.stream().map(p -> p.get("similarityFloor")).distinct().count());


    var alex = users.findByEmail("demo@nexus.local").orElseThrow();
    service.saveProfile(
      alex.id,
      new Profile(
        List.of(
          new Property("display_name", "Моё новое имя", true),
          new Property("bio", "Это описание должно сохраниться", false),
          new Property("birth_date", "1996-02-15", false),
          new Property("city", "Казань", true)
        ),
        Set.of("Игры", "Книги")
      )
    );
    alex.avatarKey = null;
    users.saveAndFlush(alex);
    var editedProfile = service.profile(alex.id, true);
    service.authenticate(
      new Credentials("ordinary@test.local", "Password123!"),
      true
    );
    demo.run();
    var profileAfterRestart = service.profile(alex.id, true);
    assertEquals(
      editedProfile.get("properties"),
      profileAfterRestart.get("properties")
    );
    assertEquals(
      editedProfile.get("interests"),
      profileAfterRestart.get("interests")
    );
    assertTrue(
      profileAfterRestart
        .get("avatarUrl")
        .toString()
        .startsWith("/api/v1/photos/")
    );
    assertEquals(15, users.count());
    var ordinary = users.findByEmail("ordinary@test.local").orElseThrow();
    assertNull(ordinary.avatarKey);
    assertFalse(service.profileComplete(ordinary.id));
  }
}
