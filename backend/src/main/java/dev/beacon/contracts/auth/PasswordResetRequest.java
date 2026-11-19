package dev.beacon.contracts.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Asks for a password-reset link to be emailed. */
public record PasswordResetRequest(
        @NotBlank @Email @Size(max = 320) String email
) {}
