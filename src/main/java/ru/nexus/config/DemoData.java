package ru.nexus.config;

import java.util.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.nexus.dto.Requests.*;
import ru.nexus.repository.UserAccountRepository;
import ru.nexus.service.NexusService;

@Component
@org.springframework.core.annotation.Order(1)
@ConditionalOnProperty(name = "nexus.demo", havingValue = "true")
public class DemoData implements CommandLineRunner {

  private final NexusService s;
  private final UserAccountRepository users;

  public DemoData(NexusService s, UserAccountRepository users) {
    this.s = s;
    this.users = users;
  }

  @org.springframework.transaction.annotation.Transactional
  public void run(String... args) {
    String[] names = { "Алекс", "Саша", "Маша", "Даня", "Аня", "Никита" };
    String[] bios = {
      "Люблю живые концерты, хороший кофе и разговоры обо всём.",
      "Ищу компанию для походов и маленьких путешествий. Играю на гитаре.",
      "Собираю истории, фотографии и билеты в новые города.",
      "Днём пишу код, вечером обсуждаю кино. Пойдём на фестиваль?",
      "Музеи, книги и длинные прогулки. Замечаю красоту в мелочах.",
      "За настолки, новые рецепты и спонтанные планы.",
    };
    for (int i = 0; i < names.length; i++) {
      String email = "demo" + (i == 0 ? "" : i) + "@nexus.local";
      if (users.findByEmail(email).isEmpty()) s.authenticate(
        new Credentials(email, "NexusDemo2026!"),
        true
      );
      Long id = users.findByEmail(email).orElseThrow().id;
      // Repair accounts left incomplete by an interrupted/older demo initialization.
      if (s.profileComplete(id)) continue;
      s.saveProfile(
        id,
        new Profile(
          List.of(
            new Property("display_name", names[i], true),
            new Property("bio", bios[i], true),
            new Property("birth_date", 2000 + i + "-04-12", false),
            new Property("city", "Москва", true)
          ),
          new LinkedHashSet<>(
            List.of("Музыка", ru.nexus.Interests.ALL.get(i + 2), "Кофе")
          )
        )
      );
    }
  }
}
