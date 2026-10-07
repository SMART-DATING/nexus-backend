package ru.nexus.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "interests")
public class Interest {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false, unique = true, length = 80)
  public String name;
}
