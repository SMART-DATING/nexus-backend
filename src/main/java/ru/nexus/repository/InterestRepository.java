package ru.nexus.repository;

import java.util.*;
import ru.nexus.entity.Interest;

public interface InterestRepository
  extends org.springframework.data.jpa.repository.JpaRepository<Interest, Long>
{
  Optional<Interest> findByName(String name);
  List<Interest> findAllByOrderByIdAsc();
  List<Interest> findByNameIn(Collection<String> names);
}
