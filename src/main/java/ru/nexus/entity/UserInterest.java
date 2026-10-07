package ru.nexus.entity;

import jakarta.persistence.*;

@Entity
@Table(
  name = "user_interests",
  uniqueConstraints = @UniqueConstraint(
    columnNames = { "user_id", "interest_id" }
  )
)
public class UserInterest {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  public UserAccount user;

  @ManyToOne(optional = false, fetch = FetchType.EAGER)
  @JoinColumn(name = "interest_id", nullable = false)
  public Interest interest;
}
