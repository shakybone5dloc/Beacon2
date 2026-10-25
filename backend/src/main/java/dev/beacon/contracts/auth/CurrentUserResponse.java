package dev.beacon.contracts.auth;

import java.util.UUID;

public record CurrentUserResponse(
        UUID id,
        String email,
        String displayName
) {
}
