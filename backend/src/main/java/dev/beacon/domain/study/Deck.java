package dev.beacon.domain.study;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** A set of study cards, usually for one application's interviews. */
@Entity
@Table(name = "study_decks")
public class Deck {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String name;

    private UUID applicationId;

    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Deck() { }

    public static Deck create(UUID ownerId, String name, UUID applicationId, Instant now) {
        var deck = new Deck();
        deck.id = UUID.randomUUID();
        deck.ownerId = ownerId;
        deck.name = name.strip();
        deck.applicationId = applicationId;
        deck.createdAt = now;
        deck.updatedAt = now;
        return deck;
    }

    public void rename(String name, Instant now) {
        this.name = name.strip();
        this.updatedAt = now;
    }

    public void touch(Instant now) { updatedAt = now; }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public UUID getApplicationId() { return applicationId; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
