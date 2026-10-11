package ru.nexus.repository;

import java.util.*;
import ru.nexus.entity.UserInterest;

public interface UserInterestRepository
  extends
    org.springframework.data.jpa.repository.JpaRepository<UserInterest, Long>
{
  interface Selection {
    Long getUserId();
    String getName();
  }
  @org.springframework.data.jpa.repository.Query("select s.user.id as userId, i.name as name from UserInterest s join s.interest i")
  List<Selection> findSelections();
  List<UserInterest> findByUserId(Long userId);
  boolean existsByUserId(Long userId);
}
