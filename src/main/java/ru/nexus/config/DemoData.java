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
    String[] names = {
      "Алекс",
      "Саша",
      "Маша",
      "Даня",
      "Аня",
      "Никита",
      "Лера",
      "Марк",
      "Соня",
      "Тимур",
      "Алиса",
      "Артём",
      "Ева",
      "Илья",
    };
    String[] bios = {
      "Люблю живые концерты, хороший кофе и разговоры обо всём.",
      "Ищу компанию для походов и маленьких путешествий. Играю на гитаре.",
      "Собираю истории, фотографии и билеты в новые города.",
      "Днём пишу код, вечером обсуждаю кино. Пойдём на фестиваль?",
      "Музеи, книги и длинные прогулки. Замечаю красоту в мелочах.",
      "За настолки, новые рецепты и спонтанные планы.",
      "Снимаю город на плёнку, ищу лучшие булочки и закаты. Покажешь своё любимое место?",
      "Бегаю по набережной и варю кофе по воскресеньям. В плейлисте — инди и немного джаза.",
      "Могу часами говорить о книгах и выставках. Мечтаю увидеть северное сияние.",
      "Разрабатываю игры, а по выходным выбираюсь на скалодром. Научу играть в любимую настолку.",
      "Пеку хлеб, хожу в походы и собираю уютные кафе. Люблю планы, которые начинаются с «а давай».",
      "Играю на барабанах и охочусь за редким винилом. Вместе проще решиться на первый урок танцев.",
      "Рисую иллюстрации, фотографирую детали и не пропускаю маленькие кинофестивали.",
      "Путешествую налегке, катаюсь на велосипеде и готовлю пасту для друзей. Ищу напарника для открытий.",
    };
    List<Set<String>> interests = List.of(
      Set.of("Музыка", "Путешествия", "Кофе"),
      Set.of("Музыка", "Книги", "Кофе"),
      Set.of("Музыка", "Технологии", "Кофе"),
      Set.of("Музыка", "Спорт", "Кофе"),
      Set.of("Музыка", "Искусство", "Кофе"),
      Set.of("Музыка", "Кофе", "Игры", "Кулинария"),
      Set.of("Фотография", "Путешествия", "Кофе"),
      Set.of("Спорт", "Музыка", "Кофе"),
      Set.of("Книги", "Искусство", "Путешествия"),
      Set.of("Технологии", "Игры", "Спорт"),
      Set.of("Кулинария", "Природа", "Кофе"),
      Set.of("Музыка", "Искусство", "Игры"),
      Set.of("Искусство", "Фотография", "Кино"),
      Set.of("Путешествия", "Спорт", "Кулинария")
    );
    int[] birthYears = {
      2000,
      2001,
      2002,
      2003,
      2004,
      2005,
      2001,
      1999,
      2002,
      2000,
      1998,
      2001,
      2003,
      1997,
    };
    for (int i = 0; i < names.length; i++) {
      String email = "demo" + (i == 0 ? "" : i) + "@nexus.local";
      if (users.findByEmail(email).isEmpty()) s.authenticate(
        new Credentials(email, "NexusDemo2026!"),
        true
      );
      var user = users.findByEmail(email).orElseThrow();
      if (user.avatarKey == null) {
        user.avatarKey = "demo-" + String.format(Locale.ROOT, "%02d", i + 1);
        users.save(user);
      }
      Long id = user.id;
      // Repair accounts left incomplete by an interrupted/older demo initialization.
      if (s.profileComplete(id)) continue;
      s.saveProfile(
        id,
        new Profile(
          List.of(
            new Property("display_name", names[i], true),
            new Property("bio", bios[i], true),
            new Property("birth_date", birthYears[i] + "-04-12", false),
            new Property("city", "Москва", true)
          ),
          interests.get(i)
        )
      );
    }
  }
}
