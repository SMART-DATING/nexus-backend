package ru.nexus.repository;

import java.util.*;
import ru.nexus.entity.UserInterest;

public interface UserInterestRepository
  extends
    org.springframework.data.jpa.repository.JpaRepository<UserInterest, Long>
{
  List<UserInterest> findByUserId(Long userId);
  boolean existsByUserId(Long userId);
}
