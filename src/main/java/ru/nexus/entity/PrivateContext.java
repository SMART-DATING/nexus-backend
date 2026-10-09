package ru.nexus.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "private_contexts")
public class PrivateContext {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false)
  public Long userId;

  @Column(nullable = false, length = 60)
  public String title;

  @Column(nullable = false, length = 6000)
  public String content;

  @Column(nullable = false, length = 16000)
  public String embedding;

  @Column(nullable = false, length = 120)
  public String modelVersion;

  @Column(nullable = false)
  public Instant updatedAt;
}
