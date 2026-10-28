package dev.beacon.contracts.auth;

import jakarta.annotation.Nullable;

import java.util.UUID;

public record CurrentUserResponse(
        UUID id,
        String email,
        @Nullable String displayName
) {
}
