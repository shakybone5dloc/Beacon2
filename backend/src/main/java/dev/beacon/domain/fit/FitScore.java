package dev.beacon.domain.fit;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * How well one resume fits one posting, as the AI judged it. Never changed once made:
 * new inputs give a new hash and a new score.
 */
@Entity
@Table(name = "fit_scores")
public class FitScore {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "resume_document_id", nullable = false, updatable = false)
    private UUID resumeDocumentId;

    /** SHA-256 of everything the score was made from; see {@code FitService}. */
    @Column(nullable = false, length = 64, updatable = false)
    private String inputHash;

    @Column(nullable = false)
    private int score;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> matchedSkills;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> gaps;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> dealbreakers;

    @Column(nullable = false, columnDefinition = "text")
    private String rationale;

    private LocalDate applyBy;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(nullable = false)
    private Instant createdAt;

    protected FitScore() {}

    public static FitScore create(UUID ownerId, UUID resumeDocumentId, String inputHash, int score,
                                  List<String> matchedSkills, List<String> gaps, List<String> dealbreakers,
                                  String rationale, LocalDate applyBy, String provider, String model, Instant now) {
        var fit = new FitScore();
        fit.id = UUID.randomUUID();
        fit.ownerId = ownerId;
        fit.resumeDocumentId = resumeDocumentId;
        fit.inputHash = inputHash;
        fit.score = score;
        fit.matchedSkills = List.copyOf(matchedSkills);
        fit.gaps = List.copyOf(gaps);
        fit.dealbreakers = List.copyOf(dealbreakers);
        fit.rationale = rationale;
        fit.applyBy = applyBy;
        fit.provider = provider;
        fit.model = model;
        fit.createdAt = now;
        return fit;
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getResumeDocumentId() { return resumeDocumentId; }
    public String getInputHash() { return inputHash; }
    public int getScore() { return score; }
    public List<String> getMatchedSkills() { return matchedSkills; }
    public List<String> getGaps() { return gaps; }
    public List<String> getDealbreakers() { return dealbreakers; }
    public String getRationale() { return rationale; }
    public LocalDate getApplyBy() { return applyBy; }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public Instant getCreatedAt() { return createdAt; }
}
