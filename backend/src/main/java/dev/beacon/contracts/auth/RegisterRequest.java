package dev.beacon.contracts.auth;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 12, max = 200, message = "must be at least 12 characters") String password,
        @Nullable @Size(max = 100) String displayName,
        /** Needed while sign-up is invite-only; case, spaces and dashes don't matter. */
        @Nullable @Size(max = 40) String inviteCode
) {
}
