package ru.nexus.repository;

import ru.nexus.entity.UserBlock;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {
  boolean existsByActorIdAndTargetId(Long actorId, Long targetId);
  Optional<UserBlock> findByActorIdAndTargetId(Long actorId, Long targetId);
  List<UserBlock> findByActorIdOrderByIdDesc(Long actorId);
}
