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
    @NotNull @Size(max = 10) Set<String> interests,
    @Pattern(regexp = "male|female|other|unspecified") String gender
  ) {
    public Profile(List<Property> properties, Set<String> interests) {
      this(properties, interests, null);
    }
  }

  public record Preferences(
    @Min(18) @Max(100) int minAge,
    @Min(18) @Max(100) int maxAge,
    @Pattern(regexp = "all|male|female|other") String interestedIn
  ) {}

  public record Message(@NotBlank @Size(max = 5000) String text) {}

  public record ReadMessages(@NotNull @PositiveOrZero Long throughId) {}

  public record ChatAction(@NotBlank @Pattern(regexp = "pin|unpin|unread|read|clear|delete") String action) {}

  public record Context(
    @NotBlank @Size(max = 60) String title,
    @NotBlank @Size(min = 20, max = 6000) String content
  ) {}

  public record PhotoOrder(@NotNull @Size(max = 6) List<@NotNull Long> ids) {}

  public record Discovery(@NotNull Boolean hidden) {}
}
