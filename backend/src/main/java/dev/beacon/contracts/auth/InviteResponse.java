package dev.beacon.contracts.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * An invite code, for the admin's list.
 *
 * @param state  OPEN (can still be used), USED, EXPIRED or REVOKED
 * @param usedBy the email of the account it made, when used
 */
public record InviteResponse(UUID id, String code, @Nullable String note,
                             @Schema(allowableValues = {"OPEN", "USED", "EXPIRED", "REVOKED"}) String state,
                             Instant createdAt, Instant expiresAt, @Nullable String usedBy,
                             @Nullable Instant usedAt, @Nullable Instant revokedAt) {}
