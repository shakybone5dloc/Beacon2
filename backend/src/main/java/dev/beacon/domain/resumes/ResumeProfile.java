package dev.beacon.domain.resumes;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What Beacon knows about one library resume, kept beside the document so listing
 * resumes never loads the file. The text is read from the file once and kept, so
 * cover letters and fit scoring don't parse the PDF every time.
 */
@Entity
@Table(name = "resume_profiles")
public class ResumeProfile {

    public enum TextStatus { PENDING, READ, UNREADABLE }

    @Id
    private UUID documentId;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(name = "is_default", nullable = false)
    private boolean defaultResume;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TextStatus textStatus;

    private String text;

    /** Titles this resume is aimed at, e.g. "Senior Java Engineer". */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> targetTitles = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> skills = new ArrayList<>();

    private Integer yearsExperience;

    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ResumeProfile() { }

    /** A profile for a just-uploaded resume, labelled after its file name. */
    public static ResumeProfile create(UUID documentId, UUID ownerId, String filename, boolean defaultResume,
                                       Optional<String> text, Instant now) {
        var profile = new ResumeProfile();
        profile.documentId = documentId;
        profile.ownerId = ownerId;
        profile.label = labelFrom(filename);
        profile.defaultResume = defaultResume;
        profile.createdAt = now;
        profile.updatedAt = now;
        profile.recordText(text);
        return profile;
    }

    public void update(String label, List<String> targetTitles, List<String> skills, Integer yearsExperience, Instant now) {
        if (label == null || label.isBlank()) throw new IllegalArgumentException("A resume needs a label");
        this.label = label.strip();
        this.targetTitles = cleaned(targetTitles);
        this.skills = cleaned(skills);
        this.yearsExperience = yearsExperience;
        this.updatedAt = now;
    }

    /** Keeps what was read from the file; empty means it had no readable text. */
    public void recordText(Optional<String> text) {
        this.text = text.orElse(null);
        this.textStatus = text.isPresent() ? TextStatus.READ : TextStatus.UNREADABLE;
    }

    public void makeDefault(Instant now) {
        this.defaultResume = true;
        this.updatedAt = now;
    }

    public void clearDefault(Instant now) {
        this.defaultResume = false;
        this.updatedAt = now;
    }

    /** "Aaron Keiper - Backend.pdf" becomes "Aaron Keiper - Backend". */
    static String labelFrom(String filename) {
        var label = filename.replaceFirst("\\.[^.]+$", "").strip();
        if (label.isEmpty()) label = "Resume";
        return label.length() <= 100 ? label : label.substring(0, 100);
    }

    private static List<String> cleaned(List<String> values) {
        if (values == null) return new ArrayList<>();
        return new ArrayList<>(values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(String::strip)
                .distinct()
                .toList());
    }

    public UUID getDocumentId() { return documentId; }
    public UUID getOwnerId() { return ownerId; }
    public String getLabel() { return label; }
    public boolean isDefaultResume() { return defaultResume; }
    public TextStatus getTextStatus() { return textStatus; }
    /** Empty until read, or when the file has no readable text. */
    public Optional<String> getText() { return Optional.ofNullable(text); }
    public List<String> getTargetTitles() { return List.copyOf(targetTitles); }
    public List<String> getSkills() { return List.copyOf(skills); }
    public Integer getYearsExperience() { return yearsExperience; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
