package ru.nexus.repository;

import ru.nexus.entity.ProfileProperty;

public interface ProfilePropertyRepository
  extends
    org.springframework.data.jpa.repository.JpaRepository<ProfileProperty, Long>
{
  java.util.List<ProfileProperty> findByUserId(Long userId);
}
