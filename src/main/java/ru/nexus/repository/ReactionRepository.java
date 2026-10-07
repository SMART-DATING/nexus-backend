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
}
