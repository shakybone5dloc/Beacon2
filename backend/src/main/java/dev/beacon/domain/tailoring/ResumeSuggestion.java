package dev.beacon.domain.tailoring;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * One change the AI suggests to tailor a resume to an application's posting (A2), such as
 * "mention Testcontainers under PropKnot". The user marks it done or dismisses it; the
 * resume itself is never changed.
 */
@Entity
@Table(name = "resume_suggestions")
public class ResumeSuggestion {

    public enum Kind { ADD, EMPHASIZE, REWORD, MOVE, CUT }

    public enum State { OPEN, DONE, DISMISSED }

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false, updatable = false)
    private UUID applicationId;

    private UUID resumeDocumentId;

    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind;

    @Column(nullable = false, length = 200)
    private String section;

    @Column(nullable = false)
    private String suggestion;

    @Column(nullable = false)
    private String reason;

    private String example;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private State state;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ResumeSuggestion() {}

    public static ResumeSuggestion create(UUID ownerId, UUID applicationId, UUID resumeDocumentId, int position,
                                          Kind kind, String section, String suggestion, String reason,
                                          String example, String model, Instant now) {
        var s = new ResumeSuggestion();
        s.id = UUID.randomUUID();
        s.ownerId = ownerId;
        s.applicationId = applicationId;
        s.resumeDocumentId = resumeDocumentId;
        s.position = position;
        s.kind = kind;
        s.section = section;
        s.suggestion = suggestion;
        s.reason = reason;
        s.example = example;
        s.state = State.OPEN;
        s.model = model;
        s.createdAt = now;
        s.updatedAt = now;
        return s;
    }

    public void mark(State state, Instant now) {
        this.state = state;
        this.updatedAt = now;
    }

    /** Kept suggestions go first when a new set is asked for. */
    public void moveTo(int position) {
        this.position = position;
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getApplicationId() { return applicationId; }
    public UUID getResumeDocumentId() { return resumeDocumentId; }
    public int getPosition() { return position; }
    public Kind getKind() { return kind; }
    public String getSection() { return section; }
    public String getSuggestion() { return suggestion; }
    public String getReason() { return reason; }
    public String getExample() { return example; }
    public State getState() { return state; }
    public String getModel() { return model; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
