package dev.beacon.domain.study;

import dev.beacon.domain.study.Scheduler.Schedule;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * One study card: a question, its answer, and when it's next due. Cards the AI or the story
 * bank made start as drafts and are only studied once the user keeps them.
 */
@Entity
@Table(name = "study_cards")
public class Card {

    public enum Source { MANUAL, BRIEF, STORY }

    public enum Status { DRAFT, ACTIVE }

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false, updatable = false)
    private UUID deckId;

    @Column(nullable = false)
    private String question;

    @Column(nullable = false)
    private String answer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Source source;

    private UUID storyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal ease;

    @Column(nullable = false)
    private int intervalDays;

    @Column(nullable = false)
    private int repetitions;

    @Column(nullable = false)
    private int lapses;

    @Column(nullable = false)
    private Instant dueAt;

    private Instant lastReviewedAt;

    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Card() { }

    public static Card create(UUID ownerId, UUID deckId, String question, String answer, Source source,
                              UUID storyId, Status status, Instant now) {
        var card = new Card();
        card.id = UUID.randomUUID();
        card.ownerId = ownerId;
        card.deckId = deckId;
        card.question = question.strip();
        card.answer = answer == null ? "" : answer.strip();
        card.source = source;
        card.storyId = storyId;
        card.status = status;
        card.schedule(Schedule.fresh(now));
        card.createdAt = now;
        card.updatedAt = now;
        return card;
    }

    public void edit(String question, String answer, Instant now) {
        this.question = question.strip();
        this.answer = answer == null ? "" : answer.strip();
        this.updatedAt = now;
    }

    /** A kept draft is due straight away. */
    public void keep(Instant now) {
        if (status == Status.ACTIVE) return;
        status = Status.ACTIVE;
        schedule(Schedule.fresh(now));
        updatedAt = now;
    }

    /**
     * Grades the card and schedules its next review, aimed at the interview when there's one
     * ahead (null when not); returns the grade's record.
     */
    public Review review(Grade grade, Instant now, Instant interviewAt) {
        var current = new Schedule(ease.doubleValue(), intervalDays, repetitions, lapses, dueAt);
        schedule(Scheduler.next(current, grade, now, interviewAt));
        lastReviewedAt = now;
        updatedAt = now;
        return Review.of(id, grade, intervalDays, now);
    }

    private void schedule(Schedule s) {
        ease = BigDecimal.valueOf(s.ease()).setScale(2, RoundingMode.HALF_UP);
        intervalDays = s.intervalDays();
        repetitions = s.repetitions();
        lapses = s.lapses();
        dueAt = s.dueAt();
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getDeckId() { return deckId; }
    public String getQuestion() { return question; }
    public String getAnswer() { return answer; }
    public Source getSource() { return source; }
    public UUID getStoryId() { return storyId; }
    public Status getStatus() { return status; }
    public BigDecimal getEase() { return ease; }
    public int getIntervalDays() { return intervalDays; }
    public int getRepetitions() { return repetitions; }
    public int getLapses() { return lapses; }
    public Instant getDueAt() { return dueAt; }
    public Instant getLastReviewedAt() { return lastReviewedAt; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
