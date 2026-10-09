package ru.nexus.entity;

import jakarta.persistence.*;

@Entity
@Table(
  name = "matches",
  uniqueConstraints = @UniqueConstraint(columnNames = { "firstId", "secondId" })
)
public class PairMatch {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public Long firstId;
  public Long secondId;
  public java.time.Instant createdAt = java.time.Instant.now();
}
