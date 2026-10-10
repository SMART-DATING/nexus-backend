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

  @Column(length = 40)
  public String avatarKey;

  @Column(columnDefinition = "bytea")
  public byte[] avatarImage;

  @Column(length = 36)
  public String avatarVersion;

  public Boolean galleryMigrated;

  // Null preserves discoverability for databases created before this setting existed.
  public Boolean discoveryHidden = false;
  public java.time.Instant lastActiveAt;
  // Nullable for existing accounts; gender is entered by its owner, never inferred.
  @Column(length = 16)
  public String gender;
}
