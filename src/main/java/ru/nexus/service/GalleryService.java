package ru.nexus.service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.nexus.entity.*;
import ru.nexus.repository.*;

@Service
@Transactional
public class GalleryService {

  private final ProfilePhotoRepository photos;
  private final UserAccountRepository users;
  private final PairMatchRepository matches;
  private final BlockService blocks;

  private record Grant(
    Long viewer,
    Long photo,
    String version,
    Instant expires
  ) {}

  private final Map<String, Grant> grants = new ConcurrentHashMap<>();
  private final Map<String, String> current = new ConcurrentHashMap<>();
  private final java.security.SecureRandom random = new java.security.SecureRandom();

  public GalleryService(
    ProfilePhotoRepository photos,
    UserAccountRepository users,
    PairMatchRepository matches,
    BlockService blocks
  ) {
    this.photos = photos;
    this.users = users;
    this.matches = matches;
    this.blocks = blocks;
  }

  public boolean canView(Long target, Long viewer) {
    if (target.equals(viewer)) return true;
    if (blocks.blocked(target, viewer)) return false;
    var user = users
      .findById(target)
      .orElseThrow(() -> NexusService.fail(404, "Анкета недоступна"));
    if (!Boolean.TRUE.equals(user.discoveryHidden)) return true;
    return (
      viewer != null &&
      matches
        .findByFirstIdAndSecondId(
          Math.min(target, viewer),
          Math.max(target, viewer)
        )
        .isPresent()
    );
  }

  public void migrate(Long userId) {
    var existing = users.findGalleryStates(List.of(userId)).stream().findFirst().orElseThrow();
    if (Boolean.TRUE.equals(existing.getGalleryMigrated())) return;
    var user = users.lockById(userId).orElseThrow();
    if (Boolean.TRUE.equals(user.galleryMigrated)) return;
    if (photos.countByUserId(userId) == 0) {
      if (user.avatarImage != null) {
        var p = newPhoto(userId, 0);
        p.image = user.avatarImage;
        photos.save(p);
      } else if (user.avatarKey != null) {
        var p = newPhoto(userId, 0);
        p.demoResource = "demo-photos/" + user.avatarKey + ".svg";
        photos.save(p);
      }
      if (user.avatarKey != null) for (int i = 1; i < 6; i++) {
        var p = newPhoto(userId, i);
        p.demoResource = "demo-photos/scene-0" + i + ".svg";
        photos.save(p);
      }
    }
    user.galleryMigrated = true;
    users.save(user);
    photos.flush();
  }

  private ProfilePhoto newPhoto(Long userId, int position) {
    var p = new ProfilePhoto();
    p.userId = userId;
    p.position = position;
    p.version = UUID.randomUUID().toString();
    return p;
  }

  public int count(Long userId) {
    migrate(userId);
    return (int) photos.countByUserId(userId);
  }

  public Map<String, Object> view(Long target, Long viewer) {
    if (!canView(target, viewer)) throw NexusService.fail(
      404,
      "Анкета недоступна"
    );
    migrate(target);
    if (viewer != null) migrate(viewer);
    var all = photos.findMetadata(List.of(target));
    int allowance =
      viewer == null ? 0 : target.equals(viewer) ? 6 : count(viewer);
    var visible = all
      .stream()
      .limit(allowance)
      .map(p ->
        Map.<String, Object>of(
          "id",
          p.getId(),
          "position",
          p.getPosition(),
          "url",
          "/api/v1/photos/" + p.getId() + "?access=" + grant(viewer, p)
        )
      )
      .toList();
    return Map.of(
      "photos",
      visible,
      "photoCount",
      all.size(),
      "photoAllowance",
      allowance
    );
  }

  // Only call after the recommendation service has applied visibility and block filters.
  public Map<Long, Map<String, Object>> eligibleViews(List<Long> targets, Long viewer) {
    if (targets.isEmpty()) return Map.of();
    migrate(viewer);
    for (var state : users.findGalleryStates(targets)) if (!Boolean.TRUE.equals(state.getGalleryMigrated())) migrate(state.getId());
    int allowance = (int) photos.countByUserId(viewer);
    Map<Long, List<ProfilePhotoRepository.Metadata>> grouped = new HashMap<>();
    for (var p : photos.findMetadata(targets)) grouped.computeIfAbsent(p.getUserId(), ignored -> new ArrayList<>()).add(p);
    Map<Long, Map<String, Object>> result = new HashMap<>();
    for (var id : targets) {
      var all = grouped.getOrDefault(id, List.of());
      var visible = all.stream().limit(allowance).map(p -> Map.<String, Object>of(
        "id", p.getId(), "position", p.getPosition(), "url", "/api/v1/photos/"+p.getId()+"?access="+grant(viewer,p)
      )).toList();
      result.put(id, Map.of("photos", visible, "photoCount", all.size(), "photoAllowance", allowance));
    }
    return result;
  }

  private synchronized String grant(Long viewer, ProfilePhotoRepository.Metadata p) {
    var now = Instant.now();
    if (grants.size() > 10000) {
      grants.entrySet().removeIf(e -> e.getValue().expires().isBefore(now));
      current.entrySet().removeIf(e -> !grants.containsKey(e.getValue()));
    }
    String key = viewer + ":" + p.getId() + ":" + p.getVersion();
    String token = current.get(key);
    var old = token == null ? null : grants.get(token);
    if (old != null && old.expires().isAfter(now.plusSeconds(30))) return token;
    byte[] random = new byte[32];
    this.random.nextBytes(random);
    token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    grants.put(token, new Grant(viewer, p.getId(), p.getVersion(), now.plusSeconds(900)));
    current.put(key, token);
    return token;
  }

  public void revoke(Long viewer) {
    grants.entrySet().removeIf(e -> e.getValue().viewer().equals(viewer));
    current.entrySet().removeIf(e -> !grants.containsKey(e.getValue()));
  }

  public record Image(byte[] bytes, String type) {}

  public Image image(Long photoId, String access) {
    var g = access == null ? null : grants.get(access);
    if (
      g == null ||
      !g.photo().equals(photoId) ||
      g.expires().isBefore(Instant.now())
    ) throw NexusService.fail(
      403,
      "Откройте анкету заново, чтобы посмотреть фото"
    );
    var p = photos
      .findById(photoId)
      .orElseThrow(() -> NexusService.fail(404, "Фото не найдено"));
    if (!canView(p.userId, g.viewer())) throw NexusService.fail(
      403,
      "Анкета скрыта"
    );
    if (!p.version.equals(g.version())) throw NexusService.fail(
      403,
      "Фото обновилось. Откройте анкету заново"
    );
    if (!p.userId.equals(g.viewer())) {
      int allowance = count(g.viewer());
      var allowed = photos
        .findMetadata(List.of(p.userId))
        .stream()
        .limit(allowance)
        .anyMatch(x -> x.getId().equals(photoId));
      if (!allowed) throw NexusService.fail(
        403,
        "Загрузите больше своих фото, чтобы увидеть это фото"
      );
    }
    if (p.image != null) return new Image(p.image, "image/jpeg");
    try (var stream = new ClassPathResource(p.demoResource).getInputStream()) {
      return new Image(stream.readAllBytes(), "image/svg+xml");
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Demo image missing", e);
    }
  }

  public Long firstId(Long userId) {
    migrate(userId);
    return photos
      .findMetadata(List.of(userId))
      .stream()
      .findFirst()
      .orElseThrow(() -> NexusService.fail(404, "Фото пока нет"))
      .getId();
  }

  public void add(Long userId, MultipartFile file) {
    byte[] image = AvatarImages.normalize(file);
    migrate(userId);
    users.lockById(userId).orElseThrow();
    var list = photos.findByUserIdOrderByPositionAscIdAsc(userId);
    if (list.size() >= 6) throw NexusService.fail(
      409,
      "Можно загрузить не больше 6 фото"
    );
    var p = newPhoto(userId, list.size());
    p.image = image;
    photos.saveAndFlush(p);
  }

  public void replace(Long userId, Long photoId, MultipartFile file) {
    byte[] image = AvatarImages.normalize(file);
    users.lockById(userId).orElseThrow();
    var p = owned(userId, photoId);
    p.image = image;
    p.demoResource = null;
    p.version = UUID.randomUUID().toString();
    photos.saveAndFlush(p);
  }

  public void replaceFirst(Long userId, MultipartFile file) {
    migrate(userId);
    var list = photos.findByUserIdOrderByPositionAscIdAsc(userId);
    if (list.isEmpty()) add(userId, file);
    else replace(userId, list.get(0).id, file);
  }

  public void delete(Long userId, Long photoId) {
    users.lockById(userId).orElseThrow();
    photos.delete(owned(userId, photoId));
    photos.flush();
    var list = photos.findByUserIdOrderByPositionAscIdAsc(userId);
    for (int i = 0; i < list.size(); i++) list.get(i).position = i;
  }

  private ProfilePhoto owned(Long userId, Long photoId) {
    var p = photos
      .findById(photoId)
      .orElseThrow(() -> NexusService.fail(404, "Фото не найдено"));
    if (!p.userId.equals(userId)) throw NexusService.fail(
      403,
      "Нельзя изменять чужое фото"
    );
    return p;
  }

  public void reorder(Long userId, List<Long> ids) {
    users.lockById(userId).orElseThrow();
    var list = photos.findByUserIdOrderByPositionAscIdAsc(userId);
    if (
      ids.size() != list.size() ||
      new HashSet<>(ids).size() != ids.size() ||
      !new HashSet<>(ids).equals(
        new HashSet<>(
          list
            .stream()
            .map(p -> p.id)
            .toList()
        )
      )
    ) throw NexusService.fail(400, "Укажите все свои фото ровно один раз");
    for (var p : list) p.position = ids.indexOf(p.id);
    photos.flush();
  }
}
