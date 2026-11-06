package dev.beacon.contracts.study;

import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * A deck with its counts.
 *
 * @param applicationLabel "Company – Role" for the application the deck prepares for
 * @param due              kept cards due now
 * @param drafts           cards made for the user to review before studying
 * @param nextDueAt        when the next kept card is due; null when the deck has none
 */
public record DeckResponse(UUID id, String name, @Nullable UUID applicationId, @Nullable String applicationLabel,
                           long cards, long due, long drafts, @Nullable Instant nextDueAt, long version,
                           Instant createdAt, Instant updatedAt) {}
