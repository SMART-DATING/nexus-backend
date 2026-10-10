package ru.nexus.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "chat_preferences", uniqueConstraints = @UniqueConstraint(columnNames = { "userId", "matchId" }))
public class ChatPreference {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;
  @Column(nullable = false) public Long userId;
  @Column(nullable = false) public Long matchId;
  public boolean pinned;
  public boolean hidden;
  public boolean markedUnread;
  public long clearedThroughId;
}
