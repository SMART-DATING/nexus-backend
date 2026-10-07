package ru.nexus.repository;

import ru.nexus.entity.SessionToken;

public interface SessionTokenRepository
  extends
    org.springframework.data.jpa.repository.JpaRepository<SessionToken, Long>
{
  java.util.Optional<SessionToken> findByTokenHash(String tokenHash);
  void deleteByTokenHash(String tokenHash);
}
