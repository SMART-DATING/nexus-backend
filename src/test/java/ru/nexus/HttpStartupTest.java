package ru.nexus;

import static org.junit.jupiter.api.Assertions.*;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

@SpringBootTest(
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = {
    "spring.datasource.url=jdbc:h2:mem:http-demo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "nexus.demo=true",
  }
)
@TestExecutionListeners(
  listeners = DependencyInjectionTestExecutionListener.class,
  inheritListeners = false
)
class HttpStartupTest {

  @LocalServerPort
  int port;

  @Test
  void loginAndConfirmationWorkOverHttpAndEmailRemainsUsable() throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      assertEquals(400, auth(client, "register", "{\"email\":\"confirm@test.local\",\"login\":\"nexus_friend\",\"password\":\"Password123!\",\"passwordConfirmation\":\"Password456!\"}").statusCode());
      assertEquals(400, auth(client, "register", "{\"email\":\"confirm@test.local\",\"login\":\"nexus_friend\",\"password\":\"Password123!\"}").statusCode());
      assertEquals(400, auth(client, "register", "{\"email\":\"confirm@test.local\",\"login\":\"invalid login\",\"password\":\"Password123!\",\"passwordConfirmation\":\"Password123!\"}").statusCode());
      assertEquals(201, auth(client, "register", "{\"email\":\"confirm@test.local\",\"login\":\"Nexus_Friend\",\"password\":\"Password123!\",\"passwordConfirmation\":\"Password123!\"}").statusCode());
      assertEquals(409, auth(client, "register", "{\"email\":\"duplicate@test.local\",\"login\":\"nexus_friend\",\"password\":\"Password123!\",\"passwordConfirmation\":\"Password123!\"}").statusCode());
      assertEquals(200, auth(client, "login", "{\"login\":\"NEXUS_FRIEND\",\"password\":\"Password123!\"}").statusCode());
      assertEquals(200, auth(client, "login", "{\"email\":\"confirm@test.local\",\"password\":\"Password123!\"}").statusCode());
      assertEquals(401, auth(client, "login", "{\"login\":\"nexus_friend\",\"password\":\"WrongPassword!\"}").statusCode());
      assertEquals(401, auth(client, "login", "{\"password\":\"Password123!\"}").statusCode());
    }
  }

  private HttpResponse<String> auth(HttpClient client, String action, String body) throws Exception {
    return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/" + action))
      .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void realHttpServerStartsWithoutAutomaticallySeedingIllustratedUsers() throws Exception {
    try (
      var client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()
    ) {
      var health = client.send(
        HttpRequest.newBuilder(
          URI.create("http://127.0.0.1:" + port + "/api/v1/health")
        )
          .timeout(Duration.ofSeconds(5))
          .build(),
        HttpResponse.BodyHandlers.ofString()
      );
      assertEquals(200, health.statusCode());
      assertTrue(health.body().contains("ok"));
      var login = client.send(
        HttpRequest.newBuilder(
          URI.create("http://127.0.0.1:" + port + "/api/v1/auth/register")
        )
          .timeout(Duration.ofSeconds(5))
          .header("Content-Type", "application/json")
          .POST(
            HttpRequest.BodyPublishers.ofString(
              "{\"email\":\"startup@test.local\",\"password\":\"Password123!\"}"
            )
          )
          .build(),
        HttpResponse.BodyHandlers.ofString()
      );
      assertEquals(201, login.statusCode());
      assertTrue(login.body().contains("accessToken"));
      assertTrue(login.body().contains("startup@test.local"));
    }
  }
}
