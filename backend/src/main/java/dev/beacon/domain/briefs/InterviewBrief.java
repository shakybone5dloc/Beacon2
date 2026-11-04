package dev.beacon.domain.briefs;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** An application's interview brief: which resume its sections are written from. */
@Entity
@Table(name = "interview_briefs")
public class InterviewBrief {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false, updatable = false)
    private UUID applicationId;

    private UUID resumeDocumentId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /** Rows are created by {@code InterviewBriefRepository.insertIfMissing}, which is safe to race. */
    protected InterviewBrief() { }

    public void useResume(UUID documentId, Instant now) {
        resumeDocumentId = documentId;
        updatedAt = now;
    }

    public void touch(Instant now) { updatedAt = now; }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getApplicationId() { return applicationId; }
    public UUID getResumeDocumentId() { return resumeDocumentId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
