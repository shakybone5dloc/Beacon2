package dev.beacon.domain.applications;

import jakarta.persistence.*;
import java.time.Instant;

/** One step in an application's status history. Only created by {@link JobApplication}. */
@Entity
@Table(name = "application_status_changes")
public class StatusChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, updatable = false)
    private JobApplication application;

    /** Null for the entry recorded when the application was created. */
    @Enumerated(EnumType.STRING)
    @Column(length = 32, updatable = false)
    private ApplicationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private ApplicationStatus toStatus;

    @Column(nullable = false, updatable = false)
    private Instant changedAt;

    // TODO(BJAVA-1): add the `source` field: type StatusChangeSource, @Enumerated(EnumType.STRING),
    //   @Column(nullable = false, length = 16, updatable = false). Copy the toStatus field above as your example.
    //   Concept: JPA maps a Java enum to a varchar column; STRING stores the name instead of the fragile ordinal.

    // TODO(BJAVA-1): add `sourceEmailId` (String, nullable, length 255, updatable = false). Column name
    //   comes from Spring's naming strategy: sourceEmailId -> source_email_id.

    protected StatusChange() { }

    // TODO(BJAVA-1): add `StatusChangeSource source, String sourceEmailId` parameters and assign them.
    //   Then fix the call in JobApplication.recordStatus. The constructor stays package-private so only the aggregate creates entries.
    StatusChange(JobApplication application, ApplicationStatus fromStatus, ApplicationStatus toStatus, Instant changedAt) {
        this.application = application;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedAt = changedAt;
    }

    public Long getId() { return id; }
    // TODO(BJAVA-1): return the real fields. These stubs only keep the project compiling.
    public StatusChangeSource getSource() { return null; }
    public String getSourceEmailId() { return null; }
    public ApplicationStatus getFromStatus() { return fromStatus; }
    public ApplicationStatus getToStatus() { return toStatus; }
    public Instant getChangedAt() { return changedAt; }
}
