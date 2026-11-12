package dev.beacon.contracts.contacts;

import dev.beacon.domain.contacts.ContactChannel;
import dev.beacon.domain.contacts.ContactDirection;
import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * One entry in the outreach log.
 *
 * @param personId      who it was with; null when it was with a team
 * @param team          the team it was with, e.g. "Hiring team"; null when it was with a person
 * @param applicationId what it was about; null for general outreach
 * @param company       that application's employer; null when undisclosed or there's no application
 * @param jobRole       that application's job title
 */
public record ContactResponse(
        UUID id,
        @Nullable UUID personId,
        @Nullable String personName,
        @Nullable String team,
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
