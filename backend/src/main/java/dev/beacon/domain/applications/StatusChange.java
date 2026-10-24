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

    protected StatusChange() { }

    StatusChange(JobApplication application, ApplicationStatus fromStatus, ApplicationStatus toStatus, Instant changedAt) {
        this.application = application;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedAt = changedAt;
    }

    public ApplicationStatus getFromStatus() { return fromStatus; }
    public ApplicationStatus getToStatus() { return toStatus; }
    public Instant getChangedAt() { return changedAt; }
}
