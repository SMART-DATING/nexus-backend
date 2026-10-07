package ru.nexus.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class UserAccount {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false, unique = true)
  public String email;

  @Column(nullable = false)
  public String passwordHash;

  public int minAge = 18;
  public int maxAge = 60;

  @ElementCollection(fetch = FetchType.EAGER)
  public java.util.Set<String> interests = new java.util.HashSet<>();
}
