package ru.nexus.repository;

import ru.nexus.entity.ChatMessage;

public interface ChatMessageRepository
  extends
    org.springframework.data.jpa.repository.JpaRepository<ChatMessage, Long>
{
  java.util.List<ChatMessage> findByMatchIdAndIdGreaterThanOrderByIdAsc(
    Long matchId,
    Long after,
    org.springframework.data.domain.Pageable pageable
  );
}
