package dev.beacon.domain.applications;

import dev.beacon.domain.DomainException;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "job_applications")
public class JobApplication {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String company;

    @Column(nullable = false, length = 200)
    private String role;

    @Column(length = 2048)
    private String jobUrl;

    @Column(length = 4000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ApplicationStatus status;

    @Version
    private Long version;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected JobApplication() { }

    public static JobApplication create(String company, String role, String jobUrl, String notes, Instant now) {
        var app = new JobApplication();
        app.id = UUID.randomUUID();
        app.applyDetails(company, role, jobUrl, notes);
        app.status = ApplicationStatus.SAVED;
        app.createdAt = now;
        app.updatedAt = now;

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

    public void changeStatus(ApplicationStatus newStatus, Instant now) {
        if (newStatus == status) {
            throw new DomainException("Application is already " + status + ".");
        }
        if (!ApplicationStatusTransitions.canMove(status, newStatus)) {
            throw new DomainException("Cannot move an application from " + status + " to " + newStatus + ".");
        }

        this.status = newStatus;
        this.updatedAt = now;
    }

    private static String trimToNull(String value) {
        return (value == null || value.isBlank() ? null : value.strip());
    }

    public UUID getId() { return id; }
    public String getCompany() { return company; }
    public String getRole() { return role; }
    public String getJobUrl() { return jobUrl; }
    public String getNotes() { return notes; }
    public ApplicationStatus getStatus() { return status; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}