package ru.nexus.repository;

import java.util.*;
import ru.nexus.entity.Preference;

public interface PreferenceRepository
  extends
    org.springframework.data.jpa.repository.JpaRepository<Preference, Long>
{
  interface DiscoveryPreference {
    Long getUserId();
    int getMinAge();
    int getMaxAge();
    String getInterestedIn();
  }
  @org.springframework.data.jpa.repository.Query("select p.user.id as userId, p.minAge as minAge, p.maxAge as maxAge, p.interestedIn as interestedIn from Preference p")
  List<DiscoveryPreference> findDiscoveryPreferences();
  Optional<Preference> findByUserId(Long userId);
}
