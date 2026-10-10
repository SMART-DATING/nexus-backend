package ru.nexus.service;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import ru.nexus.repository.UserAccountRepository;

@Service
public class PresenceService {
  private final UserAccountRepository users;
  public PresenceService(UserAccountRepository users) { this.users = users; }
  // Release the activity write before a chat operation takes ordered pair locks.
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void touch(Long id) {
    var now = Instant.now();
    users.touchActivity(id, now, now.minusSeconds(30));
  }
}
