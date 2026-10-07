package ru.nexus;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import ru.nexus.config.CatalogueData;
import ru.nexus.repository.InterestRepository;

@SpringBootTest(properties = {
  "spring.datasource.url=jdbc:h2:mem:legacy-category;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
  "spring.sql.init.mode=always",
  "spring.sql.init.schema-locations=classpath:legacy-interests.sql",
  "nexus.demo=true"
})
@TestExecutionListeners(listeners = DependencyInjectionTestExecutionListener.class, inheritListeners = false)
class LegacyInterestStartupTest {
  @Autowired InterestRepository interests;
  @Autowired CatalogueData catalogue;

  @Test
  void startsWithMandatoryLegacyCategoryAndPreservesExistingValues() {
    assertEquals(Interests.ALL.size(), interests.count());
    assertEquals("Творчество", interests.findByName("Музыка").orElseThrow().category);
    assertEquals("Общее", interests.findByName("Кофе").orElseThrow().category);
    catalogue.run();
    assertEquals(Interests.ALL.size(), interests.count());
    assertEquals("Творчество", interests.findByName("Музыка").orElseThrow().category);
  }
}

