package ru.nexus.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "sessions")
public class SessionToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(unique = true, nullable = false)
  public String tokenHash;

  public Long userId;
  public java.time.Instant expiresAt;
}
