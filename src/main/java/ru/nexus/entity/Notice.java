package ru.nexus.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "notifications")
public class Notice {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public Long userId;
  public Long matchId;
  public String text;
  public boolean seen = false;
  public java.time.Instant createdAt = java.time.Instant.now();
}
