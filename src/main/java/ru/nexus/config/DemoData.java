package ru.nexus.config;

import java.util.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.nexus.dto.Requests.*;
import ru.nexus.repository.UserAccountRepository;
import ru.nexus.service.*;

@Component
@org.springframework.core.annotation.Order(1)
@ConditionalOnProperty(name = "nexus.demo.seed-illustrated", havingValue = "true")
public class DemoData implements CommandLineRunner {
  private final NexusService s;
  private final UserAccountRepository users;
  private final ContextService contexts;
  private final SemanticEncoder semantic;
  public DemoData(NexusService s, UserAccountRepository users, ContextService contexts, SemanticEncoder semantic) {
    this.s = s; this.users = users; this.contexts = contexts; this.semantic = semantic;
  }
  @org.springframework.transaction.annotation.Transactional
  public void run(String... args) {
    // Only the developer account remains. Retired illustrated profiles must not reappear.
    String email = "demo@nexus.local";
    boolean created = users.findByEmail(email).isEmpty();
    if (created) s.authenticate(new Credentials(email, "NexusDemo2026!"), true);
    var user = users.findByEmail(email).orElseThrow();
    if (created) {
      user.avatarKey = "demo-01";
      user.galleryMigrated = false;
      users.save(user);
    }
    Long id = user.id;
    if (semantic.available() && !contexts.hasContext(id)) contexts.save(id, null, "Что для меня важно", "Мне близки спокойные разговоры без соревнования, честность и взаимное уважение. Идеальный вечер — слушать живую музыку, гулять и обсуждать услышанное за чашкой капучино.");
    if (s.profileComplete(id)) return;
    s.saveProfile(id, new Profile(List.of(
      new Property("display_name", "Алекс", true),
      new Property("bio", "Люблю живые концерты, хороший кофе и разговоры обо всём.", true),
      new Property("birth_date", "2000-04-12", false),
      new Property("city", "Москва", true)
    ), Set.of("Музыка", "Путешествия", "Кофе")));
  }
}
