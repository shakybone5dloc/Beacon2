package dev.beacon.contracts.study;

import dev.beacon.domain.study.Card;
import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * A study card.
 *
 * @param intervalDays days between this review and the next; 0 for a new or just-missed card
 * @param lapses       times it was missed after being learned
 */
public record CardResponse(UUID id, UUID deckId, String question, String answer, Card.Source source,
                           Card.Status status, Instant dueAt, int intervalDays, int repetitions, int lapses,
                           @Nullable Instant lastReviewedAt, long version) {}
