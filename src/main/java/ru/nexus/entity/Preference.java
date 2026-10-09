package ru.nexus.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "preferences")
public class Preference {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @OneToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false, unique = true)
  public UserAccount user;

  @Column(nullable = false)
  public int minAge = 18;

  @Column(nullable = false)
  public int maxAge = 60;
}
