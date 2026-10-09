package ru.nexus.entity;

import jakarta.persistence.*;

@Entity
@Table(
  name = "reactions",
  uniqueConstraints = @UniqueConstraint(columnNames = { "actorId", "targetId" })
)
public class Reaction {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public Long actorId;
  public Long targetId;
  public boolean liked;
}
