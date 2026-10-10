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
