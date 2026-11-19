package dev.beacon.contracts.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Sets a new password with the token from a reset link. */
public record NewPasswordRequest(
        @NotBlank @Size(max = 200) String token,
        @NotBlank @Size(min = 12, max = 200, message = "must be at least 12 characters") String password
) {}
