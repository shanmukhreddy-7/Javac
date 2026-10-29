package com.javac.campus.auth;

import jakarta.validation.constraints.*;

public final class AuthDtos {
  private AuthDtos() {}

  public static final String PASSWORD =
      "^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^a-zA-Z0-9]).{12,64}$";

  public record Register(
      @NotBlank @Size(min = 2, max = 80) String fullName,
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank
          @Pattern(regexp = "[A-Za-z0-9-]{3,40}", message = "Use 3–40 letters, numbers or hyphens.")
          String registrationNumber,
      @NotBlank @Size(max = 80) String department,
      @Min(1) @Max(6) int studyYear,
      @NotBlank
          @Pattern(
              regexp = PASSWORD,
              message = "Use 12–64 characters with upper/lowercase, a number and a symbol.")
          String password,
      @NotBlank String confirmPassword,
      @AssertTrue(message = "Accept the terms to continue.") boolean termsAccepted) {}

  public record Login(
      @NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 64) String password) {}

  public record EmailRequest(@NotBlank @Email @Size(max = 254) String email) {}

  public record TokenRequest(@NotBlank @Size(max = 128) String token) {}

  public record Reset(
      @NotBlank @Size(max = 128) String token,
      @NotBlank
          @Pattern(
              regexp = PASSWORD,
              message = "Use 12–64 characters with upper/lowercase, a number and a symbol.")
          String password) {}

  public record Change(
      @NotBlank @Size(max = 64) String currentPassword,
      @NotBlank
          @Pattern(
              regexp = PASSWORD,
              message = "Use 12–64 characters with upper/lowercase, a number and a symbol.")
          String password) {}

  public record Message(String message) {}
}
