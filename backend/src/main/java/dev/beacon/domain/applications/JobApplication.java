package dev.beacon.domain.applications;

import dev.beacon.domain.DomainException;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "job_applications")
public class JobApplication {

    @Id
    private UUID id;

    /** The user this application belongs to. Rows from before accounts existed have none. */
    @Column(name = "owner_id", updatable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 200)
    private String company;

    @Column(nullable = false, length = 200)
    private String role;

    @Column(length = 2048)
    private String jobUrl;

    @Column(length = 4000)
    private String notes;

    /** Last day the posting accepts applications, when it says. */
    private LocalDate applyBy;

    /** When to chase this application next. */
    private LocalDate followUpOn;

    @Column(length = 500)
    private String location;

    private Boolean remote;

    private Integer salaryMin;

    private Integer salaryMax;

    @Column(length = 3)
    private String salaryCurrency;

    @Column(length = 10)
    private String salaryPeriod;

    @Column(length = 100)
    private String salaryText;

    @Column(length = 100)
    private String source;

    private LocalDate postedOn;

    /** Full text of the ad, kept for reference and for drafting cover letters. */
    @Column(columnDefinition = "text")
    private String jobDescription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ApplicationStatus status;

    @Version
    private Long version;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private Instant statusChangedAt;

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("changedAt ASC, id ASC")
    private List<StatusChange> statusHistory = new ArrayList<>();

    protected JobApplication() { }

    public static JobApplication create(UUID ownerId, String company, String role, String jobUrl, String notes, Instant now) {
        if (ownerId == null) throw new IllegalArgumentException("An application needs an owner");
        var app = new JobApplication();
        app.id = UUID.randomUUID();
        app.ownerId = ownerId;
        app.applyDetails(company, role, jobUrl, notes);
        app.createdAt = now;
        app.updatedAt = now;
        app.recordStatus(ApplicationStatus.SAVED, now);

        return app;
    }

    public void updateDetails(String company, String role, String jobUrl, String notes, Instant now) {
        applyDetails(company, role, jobUrl, notes);
        this.updatedAt = now;
    }

    private void applyDetails(String company, String role, String jobUrl, String notes) {
        if (company == null
            || company.isBlank()
            || role == null
            || role.isBlank()) throw new IllegalArgumentException();

        this.company = company.strip();
        this.role = role.strip();
        this.jobUrl = trimToNull(jobUrl);
        this.notes = trimToNull(notes);
    }

    public void setPostingDetails(PostingDetails details, Instant now) {
        this.location = details.location();
        this.remote = details.remote();
        this.salaryMin = details.salaryMin();
        this.salaryMax = details.salaryMax();
        this.salaryCurrency = details.salaryCurrency();
        this.salaryPeriod = details.salaryPeriod();
        this.salaryText = details.salaryText();
        this.source = details.source();
        this.postedOn = details.postedOn();
        this.updatedAt = now;
    }

    public void setJobDescription(String jobDescription, Instant now) {
        this.jobDescription = jobDescription == null || jobDescription.isBlank() ? null : jobDescription.strip();
        this.updatedAt = now;
    }

    public void setApplyBy(LocalDate applyBy, Instant now) {
        this.applyBy = applyBy;
        this.updatedAt = now;
    }

    /** Pass null to clear. Past dates are allowed: they show as overdue. */
    public void scheduleFollowUp(LocalDate followUpOn, Instant now) {
        this.followUpOn = followUpOn;
        this.updatedAt = now;
    }

    public void changeStatus(ApplicationStatus newStatus, Instant now) {
        if (newStatus == status) {
            throw new DomainException("Application is already " + status + ".");
        }
        if (!ApplicationStatusTransitions.canMove(status, newStatus)) {
            throw new DomainException("Cannot move an application from " + status + " to " + newStatus + ".");
        }

        this.updatedAt = now;
        recordStatus(newStatus, now);
    }

    private void recordStatus(ApplicationStatus newStatus, Instant now) {
        statusHistory.add(new StatusChange(this, status, newStatus, now));
        this.status = newStatus;
        this.statusChangedAt = now;
    }

    private static String trimToNull(String value) {
        return (value == null || value.isBlank() ? null : value.strip());
    }

    public UUID getId() { return id; }
    public String getCompany() { return company; }
    public String getRole() { return role; }
    public String getJobUrl() { return jobUrl; }
    public String getNotes() { return notes; }
    public LocalDate getApplyBy() { return applyBy; }
    public LocalDate getFollowUpOn() { return followUpOn; }
    public PostingDetails getPostingDetails() {
        return new PostingDetails(location, remote, salaryMin, salaryMax, salaryCurrency, salaryPeriod, salaryText, source, postedOn);
    }
    public String getJobDescription() { return jobDescription; }
    /** When the application first moved to APPLIED, or null if it never has. */
    public Instant getAppliedAt() {
        return statusHistory.stream()
                .filter(change -> change.getToStatus() == ApplicationStatus.APPLIED)
                .map(StatusChange::getChangedAt)
                .findFirst()
                .orElse(null);
    }
    public ApplicationStatus getStatus() { return status; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getStatusChangedAt() { return statusChangedAt; }
    public List<StatusChange> getStatusHistory() { return Collections.unmodifiableList(statusHistory); }
}