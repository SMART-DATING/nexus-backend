package ru.nexus.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.nexus.entity.PrivateContext;

public interface PrivateContextRepository
  extends JpaRepository<PrivateContext, Long>
{
  List<PrivateContext> findByUserIdOrderByIdAsc(Long userId);
  long countByUserId(Long userId);
  boolean existsByUserId(Long userId);
}
