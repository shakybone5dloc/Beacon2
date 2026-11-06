package dev.beacon.domain.study;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** One grade given to a card, and the interval it was given as a result. */
@Entity
@Table(name = "study_reviews")
public class Review {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID cardId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Grade grade;

    @Column(nullable = false, updatable = false)
    private Instant reviewedAt;

    @Column(nullable = false, updatable = false)
    private int intervalDays;

    protected Review() { }

    static Review of(UUID cardId, Grade grade, int intervalDays, Instant now) {
        var review = new Review();
        review.id = UUID.randomUUID();
        review.cardId = cardId;
        review.grade = grade;
        review.intervalDays = intervalDays;
        review.reviewedAt = now;
        return review;
    }

    public UUID getId() { return id; }
    public UUID getCardId() { return cardId; }
    public Grade getGrade() { return grade; }
    public Instant getReviewedAt() { return reviewedAt; }
    public int getIntervalDays() { return intervalDays; }
}
