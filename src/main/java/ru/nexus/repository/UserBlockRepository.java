package ru.nexus.repository;

import ru.nexus.entity.UserBlock;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {
  @org.springframework.data.jpa.repository.Query("select b from UserBlock b where b.actorId=:id or b.targetId=:id")
  List<UserBlock> findInvolving(Long id);
  boolean existsByActorIdAndTargetId(Long actorId, Long targetId);
  Optional<UserBlock> findByActorIdAndTargetId(Long actorId, Long targetId);
  List<UserBlock> findByActorIdOrderByIdDesc(Long actorId);
}
