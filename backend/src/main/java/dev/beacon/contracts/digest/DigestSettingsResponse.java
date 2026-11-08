package dev.beacon.contracts.digest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * @param email          where it goes: the account's email address
 * @param nextSendAt     the next time it goes out, in the user's zone; null while it's off
 * @param mailConfigured whether this Beacon server can send email at all
 */
public record DigestSettingsResponse(
        boolean enabled,
        @Schema(allowableValues = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"})
        String dayOfWeek,
        int hour,
        String zone,
        String email,
        @Nullable Instant lastSentAt,
        @Nullable OffsetDateTime nextSendAt,
        boolean mailConfigured,
        long version
) {}
