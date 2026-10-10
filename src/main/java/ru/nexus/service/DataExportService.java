package ru.nexus.service;

import java.time.Instant;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.nexus.repository.*;

@Service
@Transactional
public class DataExportService {

  private final NexusService nexus;
  private final ContextService contexts;
  private final ProfilePhotoRepository photos;
  private final ReactionRepository reactions;
  private final ChatMessageRepository messages;
  private final BlockService blocks;

  public DataExportService(
    NexusService nexus,
    ContextService contexts,
    ProfilePhotoRepository photos,
    ReactionRepository reactions,
    ChatMessageRepository messages,
    BlockService blocks
  ) {
    this.nexus = nexus;
    this.contexts = contexts;
    this.photos = photos;
    this.reactions = reactions;
    this.messages = messages;
    this.blocks = blocks;
  }

  public Map<String, Object> export(Long id) {
    var account = new LinkedHashMap<>(nexus.me(id));
    @SuppressWarnings("unchecked")
    var profile = new LinkedHashMap<>(
      (Map<String, Object>) account.get("profile")
    );
    // Media grants are short-lived credentials; the export contains actual own photo data instead.
    profile.remove("avatarUrl");
    profile.remove("photos");
    profile.remove("photoAllowance");
    account.put("profile", profile);
    var images = photos
      .findByUserIdOrderByPositionAscIdAsc(id)
      .stream()
      .map(p -> {
        byte[] data = p.image;
        String type = "image/jpeg";
        if (data == null && p.demoResource != null) {
          try (
            var stream = new ClassPathResource(p.demoResource).getInputStream()
          ) {
            data = stream.readAllBytes();
            type = "image/svg+xml";
          } catch (java.io.IOException e) {
            throw new IllegalStateException("Photo resource unavailable", e);
          }
        }
        return Map.<String, Object>of(
          "position",
          p.position,
          "contentType",
          type,
          "base64",
          Base64.getEncoder().encodeToString(data == null ? new byte[0] : data)
        );
      })
      .toList();
    return Map.of(
      "formatVersion",
      1,
      "exportedAt",
      Instant.now(),
      "account",
      account,
      "photos",
      images,
      "privateContexts",
      contexts.mine(id).get("items"),
      "reactions",
      reactions
        .findByActorId(id)
        .stream()
        .map(r -> Map.of("targetUserId", r.targetId, "liked", r.liked))
        .toList(),
      "sentMessages",
      messages
        .findBySenderIdOrderByIdAsc(id)
        .stream()
        .map(m ->
          Map.of("matchId", m.matchId, "text", m.text, "createdAt", m.createdAt)
        )
        .toList(),
      "blockedUsers",
      blocks.mine(id)
    );
  }
}
