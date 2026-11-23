package dev.beacon.contracts.auth;

import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.UUID;

/** One account, as the admin's user list shows it. */
public record AdminUserResponse(
        UUID id,
        String email,
        @Nullable String displayName,
        Instant createdAt,
        boolean emailConfirmed,
        /** To within an hour; null if it hasn't been used since this was first recorded. */
        @Nullable Instant lastActiveAt,
        /** When an admin switched it off; null while it's on. */
        @Nullable Instant disabledAt,
        /** Listed in the server's admin emails, so it can't be switched off here. */
        boolean admin,
        int applicationCount
) {}
