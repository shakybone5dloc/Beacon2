package dev.beacon.contracts.auth;

import jakarta.validation.constraints.NotBlank;

/** @param rememberMe stay signed in for 30 days, across browser and server restarts */
public record LoginRequest(
        @NotBlank String email,
        @NotBlank String password,
        boolean rememberMe
) {
}
