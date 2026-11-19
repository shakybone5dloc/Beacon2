package dev.beacon.contracts.auth;

import jakarta.annotation.Nullable;

import java.util.UUID;

public record CurrentUserResponse(
        UUID id,
        String email,
        @Nullable String displayName,
        /** Can manage Beacon itself (invite codes). */
        boolean admin,
        /** The email isn't confirmed yet and this server can send the link, so the app asks for it. */
        boolean emailConfirmationNeeded
) {
}
