package dev.beacon.domain.usage;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One AI call: who made it, for what, on which model, the tokens it used and what it cost. */
@Entity
@Table(name = "ai_usage")
public class AiUsageEntry {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 40)
    private String feature;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(nullable = false)
    private long inputTokens;

    @Column(nullable = false)
    private long cachedInputTokens;

    @Column(nullable = false)
    private long outputTokens;

    @Column(nullable = false)
    private long webSearches;

    /** Null when the model had no price on file. */
    @Column(name = "cost_usd", precision = 12, scale = 6)
    private BigDecimal costUsd;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected AiUsageEntry() { }

    public static AiUsageEntry create(UUID ownerId, String feature, String provider, String model, long inputTokens,
                                      long cachedInputTokens, long outputTokens, long webSearches,
                                      BigDecimal costUsd, Instant now) {
        var entry = new AiUsageEntry();
        entry.id = UUID.randomUUID();
        entry.ownerId = ownerId;
        entry.feature = feature;
        entry.provider = provider;
        entry.model = model;
        entry.inputTokens = inputTokens;
        entry.cachedInputTokens = cachedInputTokens;
        entry.outputTokens = outputTokens;
        entry.webSearches = webSearches;
        entry.costUsd = costUsd;
        entry.createdAt = now;
        return entry;
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getFeature() { return feature; }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public long getInputTokens() { return inputTokens; }
    public long getCachedInputTokens() { return cachedInputTokens; }
    public long getOutputTokens() { return outputTokens; }
    public long getWebSearches() { return webSearches; }
    public BigDecimal getCostUsd() { return costUsd; }
    public Instant getCreatedAt() { return createdAt; }
}
