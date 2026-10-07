package ru.nexus.repository;

import java.util.*;
import ru.nexus.entity.Preference;

public interface PreferenceRepository
  extends
    org.springframework.data.jpa.repository.JpaRepository<Preference, Long>
{
  Optional<Preference> findByUserId(Long userId);
}
