package dev.beacon.domain.applications;

import dev.beacon.domain.DomainException;
import dev.beacon.domain.companies.Company;

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

    public static final String UNDISCLOSED_EMPLOYER = "Undisclosed client";

    @Id
    private UUID id;

    /** The user this application belongs to. Rows from before accounts existed have none. */
    @Column(name = "owner_id", updatable = false)
    private UUID ownerId;

    /** The employer; null when it isn't disclosed (a recruiter's client, say). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

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

    /** @param company null when the employer isn't disclosed; must belong to the same owner */
    public static JobApplication create(UUID ownerId, Company company, String role, String jobUrl, String notes, Instant now) {
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

    public void updateDetails(Company company, String role, String jobUrl, String notes, Instant now) {
        applyDetails(company, role, jobUrl, notes);
        this.updatedAt = now;
    }

    private void applyDetails(Company company, String role, String jobUrl, String notes) {
        if (role == null || role.isBlank()) throw new IllegalArgumentException("An application needs a role");
        if (company != null && !company.getOwnerId().equals(ownerId)) {
            throw new IllegalArgumentException("The company belongs to another user");
        }

        this.company = company;
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
    /** Null when the employer isn't disclosed. */
    public Company getCompany() { return company; }
    /** The employer's name, or null when it isn't disclosed. */
    public String getCompanyName() { return company == null ? null : company.getName(); }
    /** The employer's name, or "Undisclosed client", for titles and file names. */
    public String getEmployerLabel() { return company == null ? UNDISCLOSED_EMPLOYER : company.getName(); }
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