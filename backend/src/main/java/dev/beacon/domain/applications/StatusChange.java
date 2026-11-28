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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private StatusChangeSource source;

    /** The Message-ID of the email that caused this change; null for user changes. */
    @Column(length = 255, updatable = false)
    private String sourceEmailId;

    /** The change this entry undoes; null for ordinary changes. */
    @Column(name = "reverts_change_id", updatable = false)
    private Long revertsChangeId;

    protected StatusChange() { }

    StatusChange(JobApplication application, ApplicationStatus fromStatus, ApplicationStatus toStatus, Instant changedAt,
                 StatusChangeSource source, String sourceEmailId) {
        this.application = application;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedAt = changedAt;
        this.source = source;
        this.sourceEmailId = sourceEmailId;
    }

    StatusChange(JobApplication application, ApplicationStatus fromStatus, ApplicationStatus toStatus, Instant changedAt,
                 Long revertsChangeId) {
        this(application, fromStatus, toStatus, changedAt, StatusChangeSource.USER, null);
        this.revertsChangeId = revertsChangeId;
    }

    public Long getId() { return id; }
    public StatusChangeSource getSource() { return source; }
    public String getSourceEmailId() { return sourceEmailId; }
    public ApplicationStatus getFromStatus() { return fromStatus; }
    public ApplicationStatus getToStatus() { return toStatus; }
    public Instant getChangedAt() { return changedAt; }
    public Long getRevertsChangeId() { return revertsChangeId; }
}
