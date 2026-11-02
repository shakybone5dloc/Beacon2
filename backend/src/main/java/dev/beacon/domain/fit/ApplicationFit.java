package dev.beacon.domain.fit;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** Which fit score an application shows: the one checked most recently. */
@Entity
@Table(name = "application_fits")
public class ApplicationFit {

    @Id
    private UUID applicationId;

    @Column(name = "fit_score_id", nullable = false)
    private UUID fitScoreId;

    @Column(nullable = false)
    private Instant linkedAt;

    protected ApplicationFit() {}

    public static ApplicationFit create(UUID applicationId, UUID fitScoreId, Instant now) {
        var link = new ApplicationFit();
        link.applicationId = applicationId;
        link.fitScoreId = fitScoreId;
        link.linkedAt = now;
        return link;
    }

    public void relink(UUID fitScoreId, Instant now) {
        this.fitScoreId = fitScoreId;
        this.linkedAt = now;
    }

    public UUID getApplicationId() { return applicationId; }
    public UUID getFitScoreId() { return fitScoreId; }
    public Instant getLinkedAt() { return linkedAt; }
}
