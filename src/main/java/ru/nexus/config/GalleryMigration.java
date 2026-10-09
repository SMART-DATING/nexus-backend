package ru.nexus.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import ru.nexus.repository.UserAccountRepository;
import ru.nexus.service.GalleryService;

@Component
@Order(2)
public class GalleryMigration implements CommandLineRunner {

  private final UserAccountRepository users;
  private final GalleryService gallery;

  public GalleryMigration(UserAccountRepository users, GalleryService gallery) {
    this.users = users;
    this.gallery = gallery;
  }

  public void run(String... args) {
    for (var u : users.findAll()) gallery.migrate(u.id);
  }
}
