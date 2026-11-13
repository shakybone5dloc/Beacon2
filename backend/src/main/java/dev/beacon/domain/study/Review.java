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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AnswerMode mode;

    /** What the user typed or said before the reveal; null in Reveal mode. */
    @Column(updatable = false)
    private String answerText;

    /** The AI's suggested grade, when the answer was checked; the user may have chosen another. */
    @Enumerated(EnumType.STRING)
    @Column(updatable = false)
    private Grade suggestedGrade;

    @Column(updatable = false)
    private String feedback;

    protected Review() { }

    static Review of(UUID cardId, Grade grade, int intervalDays, Instant now, Answer answer) {
        var review = new Review();
        review.id = UUID.randomUUID();
        review.cardId = cardId;
        review.grade = grade;
        review.intervalDays = intervalDays;
        review.reviewedAt = now;
        review.mode = answer.mode();
        review.answerText = blankToNull(answer.text());
        review.suggestedGrade = answer.suggestedGrade();
        review.feedback = blankToNull(answer.feedback());
        return review;
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    /**
     * How the card was answered: the mode, what was typed or said, and the AI's suggested
     * grade and note when it was asked.
     */
    public record Answer(AnswerMode mode, String text, Grade suggestedGrade, String feedback) {
        public static final Answer REVEALED = new Answer(AnswerMode.REVEAL, null, null, null);

        public Answer {
            if (mode == null) mode = AnswerMode.REVEAL;
        }
    }

    public UUID getId() { return id; }
    public UUID getCardId() { return cardId; }
    public Grade getGrade() { return grade; }
    public Instant getReviewedAt() { return reviewedAt; }
    public int getIntervalDays() { return intervalDays; }
    public AnswerMode getMode() { return mode; }
    public String getAnswerText() { return answerText; }
    public Grade getSuggestedGrade() { return suggestedGrade; }
    public String getFeedback() { return feedback; }
}
