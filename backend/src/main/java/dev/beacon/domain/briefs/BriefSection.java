package dev.beacon.domain.briefs;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * One section of a brief, in Markdown. It remembers whether the AI wrote it and whether the
 * user has changed it since, so rewriting never quietly replaces the user's own words.
 */
@Entity
@Table(name = "brief_sections")
public class BriefSection {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID briefId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private SectionKind kind;

    @Column(updatable = false)
    private UUID interviewId;

    @Column(nullable = false)
    private String content;

    private Instant generatedAt;

    private String model;

    private Instant editedAt;

    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected BriefSection() { }

    public static BriefSection start(UUID briefId, SectionKind kind, UUID interviewId, Instant now) {
        var section = new BriefSection();
        section.id = UUID.randomUUID();
        section.briefId = briefId;
        section.kind = kind;
        section.interviewId = interviewId;
        section.createdAt = now;
        section.updatedAt = now;
        return section;
    }

    /** The AI's draft replaces the text, and the section counts as unedited again. */
    public void written(String content, String model, Instant now) {
        this.content = content;
        this.model = model;
        this.generatedAt = now;
        this.editedAt = null;
        this.updatedAt = now;
    }

    public void edited(String content, Instant now) {
        this.content = content;
        this.editedAt = now;
        this.updatedAt = now;
    }

    public boolean isEdited() { return editedAt != null; }

    public UUID getId() { return id; }
    public UUID getBriefId() { return briefId; }
    public SectionKind getKind() { return kind; }
    public UUID getInterviewId() { return interviewId; }
    public String getContent() { return content; }
    public Instant getGeneratedAt() { return generatedAt; }
    public String getModel() { return model; }
    public Instant getEditedAt() { return editedAt; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
