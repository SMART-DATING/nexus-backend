package ru.nexus.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "messages")
public class ChatMessage {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public Long matchId;
  public Long senderId;

  @Column(length = 5000, nullable = false)
  public String text;

  public java.time.Instant createdAt = java.time.Instant.now();
}
