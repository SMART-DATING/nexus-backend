package ru.nexus.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_blocks", uniqueConstraints = @UniqueConstraint(columnNames = {"actorId", "targetId"}))
public class UserBlock {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;
  @Column(nullable = false) public Long actorId;
  @Column(nullable = false) public Long targetId;
  @Column(nullable = false, length = 60) public String targetName;
  @Column(nullable = false) public Instant createdAt = Instant.now();
}
