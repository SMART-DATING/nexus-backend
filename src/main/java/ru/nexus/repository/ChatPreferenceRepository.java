package ru.nexus.repository;

import ru.nexus.entity.ChatPreference;
public interface ChatPreferenceRepository extends org.springframework.data.jpa.repository.JpaRepository<ChatPreference, Long> {
  java.util.Optional<ChatPreference> findByUserIdAndMatchId(Long userId, Long matchId);
  java.util.List<ChatPreference> findByUserId(Long userId);
}
