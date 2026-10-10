package ru.nexus.repository;

import ru.nexus.entity.ChatMessage;

public interface ChatMessageRepository
  extends
    org.springframework.data.jpa.repository.JpaRepository<ChatMessage, Long>
{
  @org.springframework.data.jpa.repository.Query(
    "select count(m) from ChatMessage m where m.matchId = :matchId and m.senderId <> :viewerId and m.id > :after and (m.readByRecipient = false or m.readByRecipient is null)"
  )
  long unreadCount(Long matchId, Long viewerId, Long after);

  @org.springframework.data.jpa.repository.Query("select coalesce(max(m.id), 0) from ChatMessage m where m.matchId=:matchId")
  long latestId(Long matchId);

  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query(
    "update ChatMessage m set m.readByRecipient = true where m.matchId = :matchId and m.senderId <> :viewerId and m.id <= :throughId and (m.readByRecipient = false or m.readByRecipient is null)"
  )
  int readThrough(Long matchId, Long viewerId, Long throughId);

  java.util.List<ChatMessage> findBySenderIdOrderByIdAsc(Long senderId);
  java.util.List<ChatMessage> findByMatchIdAndIdGreaterThanOrderByIdAsc(
    Long matchId,
    Long after,
    org.springframework.data.domain.Pageable pageable
  );
}
