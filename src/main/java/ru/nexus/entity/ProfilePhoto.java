package ru.nexus.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "profile_photos")
public class ProfilePhoto {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false)
  public Long userId;

  @Column(nullable = false)
  public int position;

  @Column(columnDefinition = "bytea")
  public byte[] image;

  @Column(length = 80)
  public String demoResource;

  @Column(nullable = false, length = 36)
  public String version;
}
