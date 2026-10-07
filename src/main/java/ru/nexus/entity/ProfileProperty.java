package ru.nexus.entity;

import jakarta.persistence.*;

@Entity
@Table(
  name = "profile_properties",
  uniqueConstraints = @UniqueConstraint(columnNames = { "userId", "name" })
)
public class ProfileProperty {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false)
  public Long userId;

  @Column(nullable = false)
  public String name;

  @Column(name = "property_value", length = 2000)
  public String value;

  public boolean visible = true;
}
