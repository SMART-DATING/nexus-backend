package ru.nexus.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.nexus.entity.PrivateContext;
import ru.nexus.repository.*;

@Service
@Transactional
public class ContextService {

  private final PrivateContextRepository contexts;
  private final UserAccountRepository users;
  private final SemanticEncoder encoder;
  private final ObjectMapper json;

  public ContextService(
    PrivateContextRepository contexts,
    UserAccountRepository users,
    SemanticEncoder encoder,
    ObjectMapper json
  ) {
    this.contexts = contexts;
    this.users = users;
    this.encoder = encoder;
    this.json = json;
  }

  public Map<String, Object> mine(Long userId) {
    return Map.of(
      "items",
      contexts
        .findByUserIdOrderByIdAsc(userId)
        .stream()
        .map(this::dto)
        .toList(),
      "modelAvailable",
      encoder.available(),
      "modelVersion",
      SemanticEncoder.VERSION
    );
  }

  private Map<String, Object> dto(PrivateContext c) {
    return Map.of(
      "id",
      c.id,
      "title",
      c.title,
      "content",
      c.content,
      "updatedAt",
      c.updatedAt
    );
  }

  public Object save(
    Long userId,
    Long contextId,
    String title,
    String content
  ) {
    title = title.trim();
    content = content.trim();
    if (
      title.isBlank() ||
      title.length() > 60 ||
      content.length() < 20 ||
      content.length() > 6000
    ) throw NexusService.fail(
      400,
      "Заголовок: 1–60 символов, рассказ: 20–6000 символов"
    );
    PrivateContext c =
      contextId == null ? new PrivateContext() : owned(userId, contextId);
    double[] vector = encoder.encode(content);
    users
      .lockById(userId)
      .orElseThrow(() -> NexusService.fail(404, "Пользователь не найден"));
    if (
      contextId == null && contexts.countByUserId(userId) >= 12
    ) throw NexusService.fail(409, "Можно сохранить до 12 личных рассказов");
    c.userId = userId;
    c.title = title;
    c.content = content;
    c.modelVersion = SemanticEncoder.VERSION;
    c.updatedAt = Instant.now();
    try {
      c.embedding = json.writeValueAsString(vector);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    return dto(contexts.saveAndFlush(c));
  }

  private PrivateContext owned(Long userId, Long id) {
    var c = contexts
      .findById(id)
      .orElseThrow(() -> NexusService.fail(404, "Рассказ не найден"));
    if (!c.userId.equals(userId)) throw NexusService.fail(
      403,
      "Это личный рассказ другого пользователя"
    );
    return c;
  }

  public void delete(Long userId, Long id) {
    users.lockById(userId).orElseThrow();
    contexts.delete(owned(userId, id));
  }

  public boolean hasContext(Long userId) {
    return contexts.existsByUserId(userId);
  }

  public int count(Long userId) {
    return (int) contexts.countByUserId(userId);
  }

  public int characterCount(Long userId) {
    return contexts.findByUserIdOrderByIdAsc(userId).stream()
      .mapToInt(c -> c.content.strip().length()).sum();
  }

  public double[] vector(Long userId) {
    double[] mean = null;
    for (var c : contexts.findByUserIdOrderByIdAsc(userId)) {
      double[] v;
      try {
        if (!SemanticEncoder.VERSION.equals(c.modelVersion)) {
          v = encoder.encode(c.content);
          c.embedding = json.writeValueAsString(v);
          c.modelVersion = SemanticEncoder.VERSION;
        } else v = json.readValue(c.embedding, double[].class);
      } catch (Exception e) {
        throw new IllegalStateException("Cannot read context embedding", e);
      }
      if (mean == null) mean = new double[v.length];
      for (int i = 0; i < v.length; i++) mean[i] += v[i];
    }
    if (mean != null) SemanticEncoder.normalize(mean);
    return mean;
  }
}
