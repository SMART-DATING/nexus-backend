package ru.nexus.controller;

import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.nexus.dto.Requests.*;
import ru.nexus.service.NexusService;

@RestController
@RequestMapping("/api/v1")
public class ApiController {

  private final NexusService s;

  public ApiController(NexusService s) {
    this.s = s;
  }

  @GetMapping("/health")
  public Object health() {
    return Map.of("status", "ok");
  }

  @PostMapping("/auth/register")
  @ResponseStatus(HttpStatus.CREATED)
  public Object register(@Valid @RequestBody Credentials c) {
    return s.authenticate(c, true);
  }

  @PostMapping("/auth/login")
  public Object login(@Valid @RequestBody Credentials c) {
    return s.authenticate(c, false);
  }

  @PostMapping("/auth/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(
    @RequestHeader(value = "Authorization", required = false) String h
  ) {
    s.logout(h);
  }

  @GetMapping("/users/me")
  public Object me(
    @RequestHeader(value = "Authorization", required = false) String h
  ) {
    return s.me(s.identify(h));
  }

  @GetMapping("/profiles/me")
  public Object profile(
    @RequestHeader(value = "Authorization", required = false) String h
  ) {
    return s.profile(s.identify(h), true);
  }

  @PutMapping("/profiles/me")
  public Object profile(
    @RequestHeader(value = "Authorization", required = false) String h,
    @Valid @RequestBody Profile p
  ) {
    return s.saveProfile(s.identify(h), p);
  }

  @GetMapping("/profiles/{id}")
  public Object profile(
    @RequestHeader(value = "Authorization", required = false) String h,
    @PathVariable Long id
  ) {
    Long me = s.identify(h);
    return s.profile(id, me.equals(id));
  }

  @PostMapping(value = "/profiles/me/avatar", consumes = "multipart/form-data")
  public Object uploadAvatar(
    @RequestHeader(value = "Authorization", required = false) String h,
    @RequestParam("file") org.springframework.web.multipart.MultipartFile file
  ) {
    return s.uploadAvatar(s.identify(h), file);
  }

  @DeleteMapping("/profiles/me/avatar")
  public Object removeAvatar(
    @RequestHeader(value = "Authorization", required = false) String h
  ) {
    return s.removeAvatar(s.identify(h));
  }

  @GetMapping(value = "/avatars/{id}", produces = "image/jpeg")
  public org.springframework.http.ResponseEntity<byte[]> avatar(
    @PathVariable Long id
  ) {
    return org.springframework.http.ResponseEntity.ok()
      .header("Cache-Control", "no-cache")
      .header("X-Content-Type-Options", "nosniff")
      .body(s.avatar(id));
  }

  @ExceptionHandler(
    org.springframework.web.multipart.MaxUploadSizeExceededException.class
  )
  @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
  public Object oversizedAvatar() {
    return Map.of("message", "Фото должно быть не больше 5 МБ");
  }

  @GetMapping("/interests")
  public Object interests(
    @RequestHeader(value = "Authorization", required = false) String h
  ) {
    s.identify(h);
    return Map.of("items", s.interestCatalogue());
  }

  @GetMapping("/preferences/me")
  public Object prefs(
    @RequestHeader(value = "Authorization", required = false) String h
  ) {
    return s.me(s.identify(h)).get("preferences");
  }

  @PutMapping("/preferences/me")
  public Object prefs(
    @RequestHeader(value = "Authorization", required = false) String h,
    @Valid @RequestBody Preferences p
  ) {
    return s.preferences(s.identify(h), p);
  }

  @GetMapping("/recommendations")
  public Object recommendations(
    @RequestHeader(value = "Authorization", required = false) String h,
    @RequestParam(defaultValue = "20") int limit
  ) {
    Long me = s.identify(h);
    return Map.of(
      "items",
      s.recommend(me, limit),
      "skippedCount",
      s.skippedCount(me)
    );
  }

  @PostMapping("/recommendations/restart")
  public Object restartRecommendations(
    @RequestHeader(value = "Authorization", required = false) String h
  ) {
    return s.restartRecommendations(s.identify(h));
  }

  @PostMapping("/recommendations/next")
  public Object nextRecommendations(
    @RequestHeader(value = "Authorization", required = false) String h,
    @RequestParam(defaultValue = "20") int limit
  ) {
    return s.nextRecommendations(s.identify(h), limit);
  }

  @PostMapping("/users/{id}/like")
  public Object like(
    @RequestHeader(value = "Authorization", required = false) String h,
    @PathVariable Long id
  ) {
    return s.react(s.identify(h), id, true);
  }

  @PostMapping("/users/{id}/skip")
  public Object skip(
    @RequestHeader(value = "Authorization", required = false) String h,
    @PathVariable Long id
  ) {
    return s.react(s.identify(h), id, false);
  }

  @GetMapping("/matches")
  public Object matches(
    @RequestHeader(value = "Authorization", required = false) String h
  ) {
    return Map.of("items", s.matchList(s.identify(h)));
  }

  @GetMapping({ "/matches/{id}", "/match/{id}" })
  public Object match(
    @RequestHeader(value = "Authorization", required = false) String h,
    @PathVariable Long id
  ) {
    return s.match(s.identify(h), id);
  }

  @GetMapping("/matches/{id}/messages")
  public Object history(
    @RequestHeader(value = "Authorization", required = false) String h,
    @PathVariable Long id,
    @RequestParam(defaultValue = "0") Long after
  ) {
    return Map.of("items", s.history(s.identify(h), id, after));
  }

  @PostMapping("/matches/{id}/messages")
  @ResponseStatus(HttpStatus.CREATED)
  public Object send(
    @RequestHeader(value = "Authorization", required = false) String h,
    @PathVariable Long id,
    @Valid @RequestBody Message m
  ) {
    return s.send(s.identify(h), id, m.text());
  }

  @GetMapping("/notifications")
  public Object notices(
    @RequestHeader(value = "Authorization", required = false) String h
  ) {
    return Map.of("items", s.notifications(s.identify(h)));
  }

  @PatchMapping("/notifications/{id}/read")
  public Object read(
    @RequestHeader(value = "Authorization", required = false) String h,
    @PathVariable Long id
  ) {
    return s.read(s.identify(h), id);
  }
}
