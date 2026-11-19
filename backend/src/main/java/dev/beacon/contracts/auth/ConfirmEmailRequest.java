package dev.beacon.contracts.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Confirms an email with the token from the link sent to it. */
public record ConfirmEmailRequest(
        @NotBlank @Size(max = 200) String token
) {}
