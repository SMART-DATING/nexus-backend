package ru.nexus.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public class Requests {

  public record Credentials(
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(min = 8, max = 72) String password
  ) {}

  public record Property(
    @NotBlank @Pattern(regexp = "display_name|bio|birth_date|city") String name,
    @NotNull @Size(max = 1000) String value,
    boolean visible
  ) {}

  public record Profile(
    @NotNull
    @Size(min = 4, max = 4)
    List<@jakarta.validation.Valid Property> properties,
    @NotNull @Size(min = 1, max = 10) Set<String> interests
  ) {}

  public record Preferences(
    @Min(18) @Max(100) int minAge,
    @Min(18) @Max(100) int maxAge
  ) {}

  public record Message(@NotBlank @Size(max = 5000) String text) {}
}
