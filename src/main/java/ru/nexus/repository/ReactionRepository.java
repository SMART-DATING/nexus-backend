package ru.nexus.repository;

import ru.nexus.entity.Reaction;

public interface ReactionRepository
  extends org.springframework.data.jpa.repository.JpaRepository<Reaction, Long>
{
  java.util.Optional<Reaction> findByActorIdAndTargetId(
    Long actorId,
    Long targetId
  );
  java.util.List<Reaction> findByActorId(Long actorId);

  long countByActorIdAndLikedFalse(Long actorId);

  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query(
    "delete from Reaction r where r.actorId = :actorId and r.liked = false"
  )
  int deleteSkippedByActorId(
    @org.springframework.data.repository.query.Param("actorId") Long actorId
  );
}
