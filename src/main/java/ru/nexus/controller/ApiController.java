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
  private final ru.nexus.service.GalleryService gallery;
  private final ru.nexus.service.ContextService contexts;
  private final ru.nexus.service.DataExportService dataExport;
  private final ru.nexus.service.BlockService blocks;

  public ApiController(
    NexusService s,
    ru.nexus.service.GalleryService gallery,
    ru.nexus.service.ContextService contexts,
    ru.nexus.service.DataExportService dataExport,
    ru.nexus.service.BlockService blocks
  ) {
    this.s = s;
    this.gallery = gallery;
    this.contexts = contexts;
    this.dataExport = dataExport;
    this.blocks = blocks;
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

  @PutMapping("/users/me/discovery")
  public Object discovery(
    @RequestHeader(value = "Authorization", required = false) String h,
    @Valid @RequestBody Discovery request
  ) {
    return s.discovery(s.identify(h), request.hidden());
  }

  @GetMapping("/users/me/data")
  public org.springframework.http.ResponseEntity<Object> exportData(
    @RequestHeader(value = "Authorization", required = false) String h
  ) {
    return org.springframework.http.ResponseEntity.ok()
      .header("Cache-Control", "private, no-store")
      .header("Content-Disposition", "attachment; filename=nexus-my-data.json")
      .body(dataExport.export(s.identify(h)));
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
    return s.profileFor(id, me);
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

  @GetMapping(value = "/avatars/{id}")
  public org.springframework.http.ResponseEntity<byte[]> avatar(
    @PathVariable Long id,
    @RequestParam(required = false) String access
  ) {
    return image(gallery.firstId(id), access);
  }

  @GetMapping("/photos/{id}")
  public org.springframework.http.ResponseEntity<byte[]> image(
    @PathVariable Long id,
    @RequestParam(required = false) String access
  ) {
    var image = gallery.image(id, access);
    return org.springframework.http.ResponseEntity.ok()
      .header("Content-Type", image.type())
      .header("Cache-Control", "private, no-store")
      .header("X-Content-Type-Options", "nosniff")
      .body(image.bytes());
  }

  @PostMapping(value = "/profiles/me/photos", consumes = "multipart/form-data")
  public Object addPhoto(
    @RequestHeader(value = "Authorization", required = false) String h,
    @RequestParam("file") org.springframework.web.multipart.MultipartFile file
  ) {
    var id = s.identify(h);
    gallery.add(id, file);
    return s.profile(id, true);
  }

  @PutMapping(
    value = "/profiles/me/photos/{photoId}",
    consumes = "multipart/form-data"
  )
  public Object replacePhoto(
    @RequestHeader(value = "Authorization", required = false) String h,
    @PathVariable Long photoId,
    @RequestParam("file") org.springframework.web.multipart.MultipartFile file
  ) {
    var id = s.identify(h);
    gallery.replace(id, photoId, file);
    return s.profile(id, true);
  }

  @DeleteMapping("/profiles/me/photos/{photoId}")
  public Object deletePhoto(
    @RequestHeader(value = "Authorization", required = false) String h,
    @PathVariable Long photoId
  ) {
    var id = s.identify(h);
    gallery.delete(id, photoId);
    return s.profile(id, true);
  }

  @PutMapping("/profiles/me/photos/order")
  public Object orderPhotos(
    @RequestHeader(value = "Authorization", required = false) String h,
    @Valid @RequestBody PhotoOrder order
  ) {
    var id = s.identify(h);
    gallery.reorder(id, order.ids());
    return s.profile(id, true);
  }

  @GetMapping("/contexts/me")
  public Object myContexts(
    @RequestHeader(value = "Authorization", required = false) String h
  ) {
    return contexts.mine(s.identify(h));
  }

  @PostMapping("/contexts/me")
  @ResponseStatus(HttpStatus.CREATED)
  public Object createContext(
    @RequestHeader(value = "Authorization", required = false) String h,
    @Valid @RequestBody Context context
  ) {
    return contexts.save(
      s.identify(h),
      null,
      context.title(),
      context.content()
    );
  }

  @PutMapping("/contexts/me/{id}")
  public Object updateContext(
    @RequestHeader(value = "Authorization", required = false) String h,
    @PathVariable Long id,
    @Valid @RequestBody Context context
  ) {
    return contexts.save(s.identify(h), id, context.title(), context.content());
  }

  @DeleteMapping("/contexts/me/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteContext(
    @RequestHeader(value = "Authorization", required = false) String h,
    @PathVariable Long id
  ) {
    contexts.delete(s.identify(h), id);
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

  @PostMapping("/users/{id}/block")
  public Object block(@RequestHeader(value = "Authorization", required = false) String h, @PathVariable Long id) {
    return blocks.set(s.identify(h), id, true);
  }

  @DeleteMapping("/users/{id}/block")
  public Object unblock(@RequestHeader(value = "Authorization", required = false) String h, @PathVariable Long id) {
    return blocks.set(s.identify(h), id, false);
  }

  @GetMapping("/users/me/blocks")
  public Object blockedUsers(@RequestHeader(value = "Authorization", required = false) String h) {
    return Map.of("items", blocks.mine(s.identify(h)));
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

  @PatchMapping("/matches/{id}/read")
  public Object readMessages(
    @RequestHeader(value = "Authorization", required = false) String h,
    @PathVariable Long id,
    @Valid @RequestBody ReadMessages request
  ) {
    return s.readMessages(s.identify(h), id, request.throughId());
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
