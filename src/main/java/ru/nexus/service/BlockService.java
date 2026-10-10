package ru.nexus.service;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.nexus.entity.UserBlock;
import ru.nexus.repository.*;

@Service
@Transactional
public class BlockService {
  private final UserBlockRepository blocks;
  private final UserAccountRepository users;
  private final ProfilePropertyRepository properties;
  public BlockService(UserBlockRepository blocks, UserAccountRepository users, ProfilePropertyRepository properties) {
    this.blocks = blocks; this.users = users; this.properties = properties;
  }

  public boolean blocked(Long a, Long b) {
    return a != null && b != null && (blocks.existsByActorIdAndTargetId(a, b) || blocks.existsByActorIdAndTargetId(b, a));
  }

  // Reactions, blocking and sending acquire the same two locks in this order.
  public void lockPair(Long actor, Long target) {
    users.lockById(Math.min(actor, target)).orElseThrow(() -> NexusService.fail(404, "Пользователь не найден"));
    users.lockById(Math.max(actor, target)).orElseThrow(() -> NexusService.fail(404, "Пользователь не найден"));
  }

  public Map<String, Boolean> set(Long actor, Long target, boolean enabled) {
    if (actor.equals(target)) throw NexusService.fail(400, "Нельзя заблокировать себя");
    lockPair(actor, target);
    var existing = blocks.findByActorIdAndTargetId(actor, target);
    if (enabled && existing.isEmpty()) {
      var block = new UserBlock(); block.actorId = actor; block.targetId = target;
      block.targetName = properties.findByUserId(target).stream().filter(p -> p.name.equals("display_name") && p.visible).map(p -> p.value).findFirst().orElse("Пользователь");
      blocks.saveAndFlush(block);
    } else if (!enabled && existing.isPresent()) {
      blocks.delete(existing.get()); blocks.flush();
    }
    return Map.of("blocked", enabled);
  }

  public List<Map<String, Object>> mine(Long actor) {
    return blocks.findByActorIdOrderByIdDesc(actor).stream().map(b -> Map.<String, Object>of("userId", b.targetId, "displayName", b.targetName, "createdAt", b.createdAt)).toList();
  }
}
