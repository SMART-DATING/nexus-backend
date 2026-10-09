package ru.nexus.repository;

import ru.nexus.entity.PairMatch;

public interface PairMatchRepository
  extends org.springframework.data.jpa.repository.JpaRepository<PairMatch, Long>
{
  java.util.Optional<PairMatch> findByFirstIdAndSecondId(
    Long firstId,
    Long secondId
  );
  java.util.List<PairMatch> findByFirstIdOrSecondIdOrderByIdDesc(
    Long a,
    Long b
  );
}
