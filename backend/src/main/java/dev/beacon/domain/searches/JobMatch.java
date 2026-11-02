package dev.beacon.domain.searches;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A posting a saved search found that wasn't on the board or found before. It stays
 * after the user decides on it, so a dismissed posting doesn't come back.
 */
@Entity
@Table(name = "job_matches")
public class JobMatch {

    public enum Status {
        /** Waiting for the user to save or dismiss it. */
        NEW,
        /** Saved to the board as an application. */
        SAVED,
        DISMISSED,
        /** Scored under the search's minimum; kept out of sight but remembered. */
        BELOW_MIN
    }

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID ownerId;

    private UUID savedSearchId;

    @Column(nullable = false, length = 64, updatable = false)
    private String matchKey;

    @Column(nullable = false, length = 100)
    private String source;

    @Column(nullable = false, length = 300)
    private String externalId;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(length = 300)
    private String company;

    @Column(length = 500)
    private String location;

    @Column(nullable = false)
    private boolean remote;

    @Column(nullable = false, length = 2048)
    private String url;

    private Instant postedAt;
    private LocalDate closesOn;
    private Integer salaryMin;
    private Integer salaryMax;

    @Column(length = 10)
    private String salaryCurrency;

    @Column(length = 20)
    private String salaryPeriod;

    @Column(length = 200)
    private String salaryText;

    @Column(columnDefinition = "text")
    private String snippet;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    private Integer score;
    private UUID fitScoreId;
    private UUID applicationId;

    @Column(nullable = false, updatable = false)
    private Instant foundAt;

    private Instant decidedAt;

    protected JobMatch() { }

    /** The posting's details, as a source gave them. */
    public record Posting(String source, String externalId, String title, String company, String location,
                          boolean remote, String url, Instant postedAt, LocalDate closesOn, Integer salaryMin,
                          Integer salaryMax, String salaryCurrency, String salaryPeriod, String salaryText,
                          String snippet, String description) {}

    public static JobMatch found(UUID ownerId, UUID savedSearchId, String matchKey, Posting p, Instant now) {
        var match = new JobMatch();
        match.id = UUID.randomUUID();
        match.ownerId = ownerId;
        match.savedSearchId = savedSearchId;
        match.matchKey = matchKey;
        match.source = cut(p.source(), 100);
        match.externalId = cut(p.externalId(), 300);
        match.title = cut(p.title(), 300);
        match.company = cut(p.company(), 300);
        match.location = cut(p.location(), 500);
        match.remote = p.remote();
        match.url = p.url();
        match.postedAt = p.postedAt();
        match.closesOn = p.closesOn();
        match.salaryMin = p.salaryMin();
        match.salaryMax = p.salaryMax();
        match.salaryCurrency = cut(p.salaryCurrency(), 10);
        match.salaryPeriod = cut(p.salaryPeriod(), 20);
        match.salaryText = cut(p.salaryText(), 200);
        match.snippet = p.snippet();
        match.description = p.description();
        match.status = Status.NEW;
        match.foundAt = now;
        return match;
    }

    /** A score under the minimum hides the match; without a minimum every score is shown. */
    public void scored(UUID fitScoreId, int score, Integer minScore) {
        this.fitScoreId = fitScoreId;
        this.score = score;
        if (status == Status.NEW && minScore != null && score < minScore) status = Status.BELOW_MIN;
    }

    public void savedAs(UUID applicationId, Instant now) {
        this.applicationId = applicationId;
        decide(Status.SAVED, now);
    }

    public void dismiss(Instant now) {
        decide(Status.DISMISSED, now);
    }

    /** Back to NEW, for a dismissal made by mistake. */
    public void restore() {
        status = Status.NEW;
        decidedAt = null;
    }

    private void decide(Status status, Instant now) {
        this.status = status;
        this.decidedAt = now;
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getSavedSearchId() { return savedSearchId; }
    public String getMatchKey() { return matchKey; }
    public String getSource() { return source; }
    public String getExternalId() { return externalId; }
    public String getTitle() { return title; }
    public String getCompany() { return company; }
    public String getLocation() { return location; }
    public boolean isRemote() { return remote; }
    public String getUrl() { return url; }
    public Instant getPostedAt() { return postedAt; }
    public LocalDate getClosesOn() { return closesOn; }
    public Integer getSalaryMin() { return salaryMin; }
    public Integer getSalaryMax() { return salaryMax; }
    public String getSalaryCurrency() { return salaryCurrency; }
    public String getSalaryPeriod() { return salaryPeriod; }
    public String getSalaryText() { return salaryText; }
    public String getSnippet() { return snippet; }
    public String getDescription() { return description; }
    public Status getStatus() { return status; }
    public Integer getScore() { return score; }
    public UUID getFitScoreId() { return fitScoreId; }
    public UUID getApplicationId() { return applicationId; }
    public Instant getFoundAt() { return foundAt; }
    public Instant getDecidedAt() { return decidedAt; }
}
