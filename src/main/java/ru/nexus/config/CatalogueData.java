package ru.nexus.config;

import java.util.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.nexus.Interests;
import ru.nexus.entity.*;
import ru.nexus.repository.*;

/** Seeds the catalogue and imports legacy prototype data without deleting it. */
@Component
@Order(0)
public class CatalogueData implements CommandLineRunner {

  private final InterestRepository interests;
  private final UserAccountRepository users;
  private final UserInterestRepository selected;
  private final PreferenceRepository preferences;
  private final JdbcTemplate jdbc;

  public CatalogueData(
    InterestRepository interests,
    UserAccountRepository users,
    UserInterestRepository selected,
    PreferenceRepository preferences,
    JdbcTemplate jdbc
  ) {
    this.interests = interests;
    this.users = users;
    this.selected = selected;
    this.preferences = preferences;
    this.jdbc = jdbc;
  }

  @Override
  @Transactional
  public void run(String... args) {
    for (String name : Interests.ALL) {
      if (interests.findByName(name).isEmpty()) {
        Interest i = new Interest();
        i.name = name;
        interests.save(i);
      }
    }
    Set<String> columns = new HashSet<>(
      jdbc.query(
        "select table_name, column_name from information_schema.columns",
        (r, n) ->
          r.getString(1).toLowerCase(Locale.ROOT) +
          "." +
          r.getString(2).toLowerCase(Locale.ROOT)
      )
    );
    for (UserAccount u : users.findAll()) {
      if (preferences.findByUserId(u.id).isEmpty()) {
        Preference p = new Preference();
        p.user = u;
        if (
          columns.contains("users.min_age") && columns.contains("users.max_age")
        ) {
          jdbc.query(
            "select min_age,max_age from users where id=?",
            (org.springframework.jdbc.core.RowCallbackHandler) r -> {
              p.minAge = r.getInt(1);
              p.maxAge = r.getInt(2);
            },
            u.id
          );
        }
        preferences.save(p);
      }
      if (
        columns.contains("user_account_interests.interests") &&
        !selected.existsByUserId(u.id)
      ) {
        for (String name : jdbc.queryForList(
          "select interests from user_account_interests where user_account_id=?",
          String.class,
          u.id
        )) {
          Interest i = interests.findByName(name).orElseGet(() -> {
            Interest x = new Interest();
            x.name = name;
            return interests.save(x);
          });
          UserInterest link = new UserInterest();
          link.user = u;
          link.interest = i;
          selected.save(link);
        }
      }
    }
  }
}
