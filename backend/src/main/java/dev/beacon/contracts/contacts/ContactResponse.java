package dev.beacon.contracts.contacts;

import dev.beacon.domain.contacts.ContactChannel;
import dev.beacon.domain.contacts.ContactDirection;
import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * One entry in the outreach log.
 *
 * @param applicationId what it was about; null for general outreach
 * @param company       that application's employer; null when undisclosed or there's no application
 * @param jobRole       that application's job title
 */
public record ContactResponse(
        UUID id,
        UUID personId,
        String personName,
        @Nullable UUID applicationId,
        @Nullable String company,
        @Nullable String jobRole,
        ContactChannel channel,
        ContactDirection direction,
        Instant occurredAt,
        @Nullable String body,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
}
