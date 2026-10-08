package ru.nexus;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;

@SpringBootTest(
  properties = {
    "spring.datasource.url=jdbc:h2:mem:test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "nexus.demo=false",
  }
)
@AutoConfigureMockMvc
@org.springframework.test.context.TestExecutionListeners(
  listeners = {
    org.springframework.test.context.support.DependencyInjectionTestExecutionListener.class,
    org.springframework.test.context.web.ServletTestExecutionListener.class,
  },
  inheritListeners = false
)
class ApiIntegrationTest {

  @Autowired
  MockMvc mvc;

  @Autowired
  ObjectMapper json;

  record Account(long id, String token) {}

  Account register(String name) throws Exception {
    var r = call(
      "POST",
      "/auth/register",
      null,
      Map.of(
        "email",
        name + UUID.randomUUID() + "@test.local",
        "password",
        "Password123!"
      ),
      201
    );
    return new Account(
      r.path("user").path("id").asLong(),
      r.path("accessToken").asText()
    );
  }

  JsonNode call(
    String method,
    String path,
    String token,
    Object data,
    int status
  ) throws Exception {
    var req =
      org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(
        org.springframework.http.HttpMethod.valueOf(method),
        "/api/v1" + path
      );
    if (token != null) req.header("Authorization", "Bearer " + token);
    if (data != null) req
      .contentType(MediaType.APPLICATION_JSON)
      .content(json.writeValueAsString(data));
    String body = mvc
      .perform(req)
      .andExpect(status().is(status))
      .andReturn()
      .getResponse()
      .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    return body.isEmpty() ? json.nullNode() : json.readTree(body);
  }

  Map<String, Object> profile(String name, String birth) {
    return Map.of(
      "properties",
      List.of(
        Map.of("name", "display_name", "value", name, "visible", true),
        Map.of("name", "bio", "value", "Private biography", "visible", false),
        Map.of("name", "birth_date", "value", birth, "visible", false),
        Map.of("name", "city", "value", "Москва", "visible", true)
      ),
      "interests",
      List.of("Музыка", "Кофе")
    );
  }

  void fill(Account a, String name) throws Exception {
    call("PUT", "/profiles/me", a.token, profile(name, "2001-04-12"), 200);
  }

  @Test
  void completeJourneyAndAccessBoundaries() throws Exception {
    Account a = register("a"),
      b = register("b"),
      outsider = register("c");
    fill(a, "Alex");
    fill(b, "Sam");
    fill(outsider, "Other");
    assertEquals(
      401,
      call("GET", "/users/me", null, null, 401).path("status").asInt()
    );
    var publicProfile = call("GET", "/profiles/" + a.id, b.token, null, 200);
    assertEquals(2, publicProfile.path("properties").size());
    assertFalse(publicProfile.toString().contains("Private biography"));
    assertFalse(publicProfile.toString().contains("2001-04-12"));
    var recommendations = call(
      "GET",
      "/recommendations",
      a.token,
      null,
      200
    ).path("items");
    assertTrue(
      java.util.stream.StreamSupport.stream(
        recommendations.spliterator(),
        false
      ).noneMatch(x -> x.path("userId").asLong() == a.id)
    );
    call("POST", "/users/" + a.id + "/like", a.token, null, 400);
    assertFalse(
      call("POST", "/users/" + b.id + "/like", a.token, null, 200)
        .path("matched")
        .asBoolean()
    );
    var match = call("POST", "/users/" + a.id + "/like", b.token, null, 200);
    assertTrue(match.path("matched").asBoolean());
    long mid = match.path("matchId").asLong();
    call("POST", "/users/" + b.id + "/like", a.token, null, 409);
    call("GET", "/matches/" + mid, outsider.token, null, 403);
    call("GET", "/matches/" + mid + "/messages", outsider.token, null, 403);
    call(
      "POST",
      "/matches/" + mid + "/messages",
      outsider.token,
      Map.of("text", "intrusion"),
      403
    );
    call(
      "POST",
      "/matches/" + mid + "/messages",
      a.token,
      Map.of("text", "   "),
      400
    );
    var message = call(
      "POST",
      "/matches/" + mid + "/messages",
      a.token,
      Map.of("text", "Привет, Sam!"),
      201
    );
    assertEquals(
      "Привет, Sam!",
      call("GET", "/matches/" + mid + "/messages", b.token, null, 200)
        .path("items")
        .get(0)
        .path("text")
        .asText()
    );
    assertEquals(
      0,
      call(
        "GET",
        "/matches/" + mid + "/messages?after=" + message.path("id").asLong(),
        b.token,
        null,
        200
      )
        .path("items")
        .size()
    );
    long nid = call("GET", "/notifications", b.token, null, 200)
      .path("items")
      .get(0)
      .path("id")
      .asLong();
    call("PATCH", "/notifications/" + nid + "/read", outsider.token, null, 403);
    assertTrue(
      call("PATCH", "/notifications/" + nid + "/read", b.token, null, 200)
        .path("seen")
        .asBoolean()
    );
    call("POST", "/auth/logout", a.token, null, 204);
    call("GET", "/users/me", a.token, null, 401);
  }

  @Test
  void validationAndPreferences() throws Exception {
    Account a = register("v"),
      b = register("w");
    call("GET", "/recommendations", a.token, null, 409);
    call(
      "PUT",
      "/profiles/me",
      a.token,
      profile("Minor", java.time.LocalDate.now().minusYears(17).toString()),
      400
    );
    fill(a, "Valid");
    fill(b, "Other");
    call(
      "PUT",
      "/preferences/me",
      a.token,
      Map.of("minAge", 40, "maxAge", 18),
      400
    );
    call(
      "PUT",
      "/preferences/me",
      a.token,
      Map.of("minAge", 90, "maxAge", 100),
      200
    );
    assertEquals(
      0,
      call("GET", "/recommendations", a.token, null, 200).path("items").size()
    );
    call(
      "PUT",
      "/preferences/me",
      a.token,
      Map.of("minAge", 18, "maxAge", 100),
      200
    );
    call("POST", "/users/" + b.id + "/skip", a.token, null, 200);
    var items = call("GET", "/recommendations", a.token, null, 200).path(
      "items"
    );
    assertTrue(
      java.util.stream.StreamSupport.stream(
        items.spliterator(),
        false
      ).noneMatch(x -> x.path("userId").asLong() == b.id)
    );
    call(
      "POST",
      "/auth/register",
      null,
      Map.of("email", "bad", "password", "short"),
      400
    );
    call(
      "POST",
      "/auth/login",
      null,
      Map.of("email", "missing@test.local", "password", "Password123!"),
      401
    );
  }

  @Test
  void restartingRecommendationsRestoresOnlyOwnSkipsAndPreservesConversations()
    throws Exception {
    Account a = register("restart-a"),
      b = register("restart-b"),
      skipped = register("restart-skipped"),
      outsider = register("restart-outsider");
    fill(a, "Alex");
    fill(b, "Sam");
    fill(skipped, "Taylor");
    fill(outsider, "Other");
    call("POST", "/users/" + b.id + "/like", a.token, null, 200);
    long mid = call("POST", "/users/" + a.id + "/like", b.token, null, 200)
      .path("matchId")
      .asLong();
    call(
      "POST",
      "/matches/" + mid + "/messages",
      a.token,
      Map.of("text", "Сохрани эту переписку"),
      201
    );
    call("POST", "/users/" + skipped.id + "/skip", a.token, null, 200);
    call("POST", "/users/" + skipped.id + "/skip", outsider.token, null, 200);
    assertEquals(
      1,
      call("GET", "/recommendations", a.token, null, 200)
        .path("skippedCount")
        .asInt()
    );
    call("POST", "/recommendations/restart", null, null, 401);
    assertEquals(
      1,
      call("POST", "/recommendations/restart", a.token, null, 200)
        .path("restored")
        .asInt()
    );

    var recommendations = call(
      "GET",
      "/recommendations?limit=50",
      a.token,
      null,
      200
    );
    assertEquals(0, recommendations.path("skippedCount").asInt());
    var recommendedIds = new HashSet<Long>();
    recommendations
      .path("items")
      .forEach(p -> recommendedIds.add(p.path("userId").asLong()));
    assertTrue(recommendedIds.contains(skipped.id));
    assertFalse(recommendedIds.contains(b.id));
    assertEquals(
      1,
      call("GET", "/recommendations", outsider.token, null, 200)
        .path("skippedCount")
        .asInt()
    );
    call("POST", "/users/" + skipped.id + "/skip", outsider.token, null, 409);
    call("POST", "/users/" + b.id + "/like", a.token, null, 409);
    assertEquals(
      1,
      call("GET", "/matches", a.token, null, 200).path("items").size()
    );
    assertEquals(
      "Сохрани эту переписку",
      call("GET", "/matches/" + mid + "/messages", b.token, null, 200)
        .path("items")
        .get(0)
        .path("text")
        .asText()
    );
    assertEquals(
      0,
      call("POST", "/recommendations/restart", a.token, null, 200)
        .path("restored")
        .asInt()
    );
    call("POST", "/users/" + skipped.id + "/like", a.token, null, 200);
  }

  @Test
  void concurrentReciprocalLikesCreateOneMatch() throws Exception {
    Account a = register("concurrent-a"),
      b = register("concurrent-b");
    fill(a, "A");
    fill(b, "B");
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var gate = new java.util.concurrent.CountDownLatch(1);
      var first = pool.submit(() -> {
        gate.await();
        return call("POST", "/users/" + b.id + "/like", a.token, null, 200);
      });
      var second = pool.submit(() -> {
        gate.await();
        return call("POST", "/users/" + a.id + "/like", b.token, null, 200);
      });
      gate.countDown();
      first.get(15, java.util.concurrent.TimeUnit.SECONDS);
      second.get(15, java.util.concurrent.TimeUnit.SECONDS);
    }
    assertEquals(
      1,
      call("GET", "/matches", a.token, null, 200).path("items").size()
    );
  }
}
