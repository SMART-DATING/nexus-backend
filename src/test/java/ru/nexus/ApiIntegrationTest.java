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
    call(
      "POST",
      "/contexts/me",
      a.token,
      Map.of(
        "title",
        "Обо мне",
        "content",
        "Люблю живую музыку, прогулки и спокойные разговоры. Ценю доверие и поддержку."
      ),
      201
    );
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

  @Test
  void circularFeedFinishesUnseenBeforeRepeatingSkipsAndPreservesLikes()
    throws Exception {
    Account a = register("circle-a"),
      b = register("circle-b"),
      c = register("circle-c"),
      d = register("circle-d");
    fill(a, "Circle A");
    fill(b, "Circle B");
    fill(c, "Circle C");
    fill(d, "Circle D");
    call("POST", "/recommendations/next", null, null, 401);
    call("POST", "/users/" + b.id + "/like", a.token, null, 200);
    long mid = call("POST", "/users/" + a.id + "/like", b.token, null, 200)
      .path("matchId")
      .asLong();
    call(
      "POST",
      "/matches/" + mid + "/messages",
      a.token,
      Map.of("text", "Круг не удаляет чат"),
      201
    );
    for (var p : call(
      "GET",
      "/recommendations?limit=50",
      a.token,
      null,
      200
    ).path("items"))
      if (p.path("userId").asLong() != d.id) call(
        "POST",
        "/users/" + p.path("userId").asLong() + "/skip",
        a.token,
        null,
        200
      );
    var unseen = call(
      "POST",
      "/recommendations/next?limit=50",
      a.token,
      null,
      200
    );
    assertFalse(unseen.path("cycleRestarted").asBoolean());
    assertEquals(1, unseen.path("items").size());
    assertEquals(d.id, unseen.path("items").get(0).path("userId").asLong());
    call("POST", "/users/" + d.id + "/skip", a.token, null, 200);
    var repeated = call(
      "POST",
      "/recommendations/next?limit=50",
      a.token,
      null,
      200
    );
    assertTrue(repeated.path("cycleRestarted").asBoolean());
    var ids = new HashSet<Long>();
    repeated.path("items").forEach(p -> ids.add(p.path("userId").asLong()));
    assertTrue(ids.containsAll(Set.of(c.id, d.id)));
    assertFalse(ids.contains(b.id));
    assertFalse(ids.contains(a.id));
    assertEquals(
      "Круг не удаляет чат",
      call("GET", "/matches/" + mid + "/messages", b.token, null, 200)
        .path("items")
        .get(0)
        .path("text")
        .asText()
    );
    call("POST", "/users/" + c.id + "/skip", a.token, null, 200);
    call(
      "PUT",
      "/preferences/me",
      a.token,
      Map.of("minAge", 100, "maxAge", 100),
      200
    );
    var outside = call("POST", "/recommendations/next", a.token, null, 200);
    assertTrue(outside.path("items").isEmpty());
    assertFalse(outside.path("cycleRestarted").asBoolean());
    assertEquals(1, outside.path("skippedCount").asInt());
  }

  @Test
  void avatarUploadIsBoundedDecodedPersistedAndOwnedByCurrentUser()
    throws Exception {
    Account a = register("photo-a"),
      b = register("photo-b");
    fill(a, "Photo A");
    fill(b, "Photo B");
    var source = new java.awt.image.BufferedImage(
      1500,
      800,
      java.awt.image.BufferedImage.TYPE_INT_RGB
    );
    var bytes = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(source, "png", bytes);
    var file = new org.springframework.mock.web.MockMultipartFile(
      "file",
      "../../portrait.png",
      "image/png",
      bytes.toByteArray()
    );
    mvc
      .perform(multipart("/api/v1/profiles/me/avatar").file(file))
      .andExpect(status().isUnauthorized());
    var uploaded = mvc
      .perform(
        multipart("/api/v1/profiles/me/avatar")
          .file(file)
          .header("Authorization", "Bearer " + a.token)
      )
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString();
    String url = json.readTree(uploaded).path("avatarUrl").asText();
    assertTrue(url.startsWith("/api/v1/photos/"));
    assertEquals(
      url,
      call("GET", "/profiles/me", a.token, null, 200).path("avatarUrl").asText()
    );
    assertFalse(
      call("GET", "/profiles/me", b.token, null, 200).has("avatarUrl")
    );
    var downloaded = mvc
      .perform(get(url))
      .andExpect(status().isOk())
      .andExpect(content().contentType("image/jpeg"))
      .andExpect(header().string("X-Content-Type-Options", "nosniff"))
      .andReturn()
      .getResponse()
      .getContentAsByteArray();
    var image = javax.imageio.ImageIO.read(
      new java.io.ByteArrayInputStream(downloaded)
    );
    assertEquals(1200, image.getWidth());
    assertEquals(640, image.getHeight());
    var invalid = new org.springframework.mock.web.MockMultipartFile(
      "file",
      "fake.jpg",
      "image/jpeg",
      "<svg onload='alert(1)'/>".getBytes()
    );
    mvc
      .perform(
        multipart("/api/v1/profiles/me/avatar")
          .file(invalid)
          .header("Authorization", "Bearer " + a.token)
      )
      .andExpect(status().isBadRequest());
    var oversized = new org.springframework.mock.web.MockMultipartFile(
      "file",
      "large.png",
      "image/png",
      new byte[5 * 1024 * 1024 + 1]
    );
    mvc
      .perform(
        multipart("/api/v1/profiles/me/avatar")
          .file(oversized)
          .header("Authorization", "Bearer " + a.token)
      )
      .andExpect(status().isPayloadTooLarge());
    assertEquals(
      url,
      call("GET", "/profiles/me", a.token, null, 200).path("avatarUrl").asText()
    );
    mvc
      .perform(
        multipart("/api/v1/profiles/me/avatar")
          .file(file)
          .header("Authorization", "Bearer " + a.token)
      )
      .andExpect(status().isOk());
    assertNotEquals(
      url,
      call("GET", "/profiles/me", a.token, null, 200).path("avatarUrl").asText()
    );
    call("DELETE", "/profiles/me/avatar", b.token, null, 200);
    mvc
      .perform(
        get(
          call("GET", "/profiles/me", a.token, null, 200)
            .path("avatarUrl")
            .asText()
        )
      )
      .andExpect(status().isOk());
    call("DELETE", "/profiles/me/avatar", a.token, null, 200);
    mvc
      .perform(get("/api/v1/avatars/" + a.id))
      .andExpect(status().isNotFound());
    call("GET", "/users/me", null, null, 401);
  }

  @Test
  void privateContextsOwnOnlyAndSemanticRankingChangesAfterEditing()
    throws Exception {
    Account a = register("semantic-a"),
      b = register("semantic-b"),
      c = register("semantic-c");
    for (var u : List.of(a, b, c))
      call(
        "PUT",
        "/profiles/me",
        u.token,
        profile(
          "Semantic",
          java.time.LocalDate.now().minusYears(89).toString()
        ),
        200
      );
    call(
      "PUT",
      "/preferences/me",
      a.token,
      Map.of("minAge", 89, "maxAge", 89),
      200
    );
    String books =
      "Люблю читать романы и обсуждать литературу. Книги помогают мне понимать людей.";
    String hiking =
      "Мне нравятся пешие походы, горы, палатки и ночёвки на природе вдали от города.";
    var note = call(
      "POST",
      "/contexts/me",
      a.token,
      Map.of("title", "Личное", "content", books),
      201
    );
    call(
      "POST",
      "/contexts/me",
      b.token,
      Map.of(
        "title",
        "Личное B",
        "content",
        "Обожаю книги, литературные встречи и разговоры о писателях и романах."
      ),
      201
    );
    call(
      "POST",
      "/contexts/me",
      c.token,
      Map.of("title", "Личное C", "content", hiking),
      201
    );
    var first = call("GET", "/recommendations", a.token, null, 200).path(
      "items"
    );
    assertEquals(b.id, first.get(0).path("userId").asLong());
    assertFalse(first.toString().contains("content"));
    assertFalse(first.toString().contains("embedding"));
    assertFalse(
      call("GET", "/profiles/" + a.id, b.token, null, 200)
        .toString()
        .contains(books)
    );
    String path = "/contexts/me/" + note.path("id").asLong();
    call(
      "PUT",
      path,
      b.token,
      Map.of("title", "Hacked", "content", hiking),
      403
    );
    call("DELETE", path, b.token, null, 403);
    call(
      "PUT",
      path,
      a.token,
      Map.of("title", "Новый взгляд", "content", hiking),
      200
    );
    assertEquals(
      c.id,
      call("GET", "/recommendations", a.token, null, 200)
        .path("items")
        .get(0)
        .path("userId")
        .asLong()
    );
    var extra = call(
      "POST",
      "/contexts/me",
      a.token,
      Map.of("title", "Ещё", "content", books),
      201
    );
    assertEquals(
      2,
      call("GET", "/contexts/me", a.token, null, 200).path("items").size()
    );
    call(
      "DELETE",
      "/contexts/me/" + extra.path("id").asLong(),
      a.token,
      null,
      204
    );
    call("DELETE", path, a.token, null, 204);
    call("GET", "/recommendations", a.token, null, 409);
  }

  JsonNode addPhoto(Account a, int expectedStatus) throws Exception {
    var image = new java.awt.image.BufferedImage(
      10,
      10,
      java.awt.image.BufferedImage.TYPE_INT_RGB
    );
    var out = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(image, "png", out);
    var file = new org.springframework.mock.web.MockMultipartFile(
      "file",
      "photo.png",
      "image/png",
      out.toByteArray()
    );
    var body = mvc
      .perform(
        multipart("/api/v1/profiles/me/photos")
          .file(file)
          .header("Authorization", "Bearer " + a.token)
      )
      .andExpect(status().is(expectedStatus))
      .andReturn()
      .getResponse()
      .getContentAsString();
    return json.readTree(body);
  }

  @Test
  void sixPhotoQuotaProtectsUrlsAndPreviouslyIssuedMediaAfterQuotaDrops()
    throws Exception {
    Account a = register("gallery-a"),
      b = register("gallery-b");
    for (int i = 0; i < 6; i++) addPhoto(b, 200);
    addPhoto(b, 409);
    var hidden = call("GET", "/profiles/" + b.id, a.token, null, 200);
    assertEquals(6, hidden.path("photoCount").asInt());
    assertEquals(0, hidden.path("photos").size());
    assertFalse(hidden.has("avatarUrl"));
    addPhoto(a, 200);
    assertEquals(
      1,
      call("GET", "/profiles/" + b.id, a.token, null, 200)
        .path("photos")
        .size()
    );
    var own = addPhoto(a, 200);
    var visible = call("GET", "/profiles/" + b.id, a.token, null, 200).path(
      "photos"
    );
    assertEquals(2, visible.size());
    String second = visible.get(1).path("url").asText();
    long bid = visible.get(1).path("id").asLong();
    mvc
      .perform(get(second))
      .andExpect(status().isOk())
      .andExpect(header().string("Cache-Control", "private, no-store"));
    mvc.perform(get("/api/v1/photos/" + bid)).andExpect(status().isForbidden());
    call("DELETE", "/profiles/me/photos/" + bid, a.token, null, 403);
    call(
      "DELETE",
      "/profiles/me/photos/" + own.path("photos").get(1).path("id").asLong(),
      a.token,
      null,
      200
    );
    mvc.perform(get(second)).andExpect(status().isForbidden());
    var bphotos = call("GET", "/profiles/me", b.token, null, 200).path(
      "photos"
    );
    var ids = new ArrayList<Long>();
    bphotos.forEach(x -> ids.add(x.path("id").asLong()));
    Collections.reverse(ids);
    call("PUT", "/profiles/me/photos/order", b.token, Map.of("ids", ids), 200);
    assertEquals(
      ids.get(0).longValue(),
      call("GET", "/profiles/" + b.id, a.token, null, 200)
        .path("photos")
        .get(0)
        .path("id")
        .asLong()
    );
    call("PUT", "/profiles/me/photos/order", a.token, Map.of("ids", ids), 400);
  }

  @Autowired
  ru.nexus.service.GalleryService gallery;

  @Autowired
  ru.nexus.repository.UserAccountRepository userRepository;

  @Test
  void legacyPhotoMigratesOnceAndDeletedPhotoDoesNotReturn() throws Exception {
    var a = register("legacy-photo");
    var u = userRepository.findById(a.id).orElseThrow();
    u.avatarImage = new byte[] { 1, 2, 3 };
    u.avatarVersion = "legacy";
    u.galleryMigrated = false;
    userRepository.saveAndFlush(u);
    gallery.migrate(a.id);
    assertEquals(1, gallery.count(a.id));
    long photo = gallery.firstId(a.id);
    gallery.migrate(a.id);
    assertEquals(photo, gallery.firstId(a.id));
    gallery.delete(a.id, photo);
    gallery.migrate(a.id);
    assertEquals(0, gallery.count(a.id));
  }

  @Test
  void twoConcurrentRecommendationRequestsDoNotLockEachOthersGallery()
    throws Exception {
    var a = register("concurrent-rec-a");
    var b = register("concurrent-rec-b");
    fill(a, "A");
    fill(b, "B");
    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      var start = new java.util.concurrent.CountDownLatch(1);
      var left = pool.submit(() -> {
        start.await();
        return call("POST", "/recommendations/next", a.token, null, 200);
      });
      var right = pool.submit(() -> {
        start.await();
        return call("POST", "/recommendations/next", b.token, null, 200);
      });
      start.countDown();
      assertFalse(
        left
          .get(10, java.util.concurrent.TimeUnit.SECONDS)
          .path("items")
          .isEmpty()
      );
      assertFalse(
        right
          .get(10, java.util.concurrent.TimeUnit.SECONDS)
          .path("items")
          .isEmpty()
      );
    } finally {
      pool.shutdownNow();
    }
  }
}
