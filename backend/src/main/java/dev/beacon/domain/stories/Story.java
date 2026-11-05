package dev.beacon.domain.stories;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * One STAR story from the user's story bank: what happened, what they did and how it
 * turned out, with the numbers behind it and tags for the questions it answers.
 */
@Entity
@Table(name = "stories")
public class Story {

    /**
     * A placeholder still to fill in, like "[how many users?]". A Markdown link's text,
     * followed by "(", is not one.
     */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\[([^\\]\\n]{1,80})\\](?!\\()");

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String situation;

    @Column(nullable = false)
    private String task;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String result;

    @Column(nullable = false)
    private String metrics;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> tags = new ArrayList<>();

    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Story() { }

    public static Story create(UUID ownerId, Fields fields, Instant now) {
        var story = new Story();
        story.id = UUID.randomUUID();
        story.ownerId = ownerId;
        story.createdAt = now;
        story.apply(fields, now);
        return story;
    }

    public void apply(Fields fields, Instant now) {
        this.title = fields.title().strip();
        this.situation = clean(fields.situation());
        this.task = clean(fields.task());
        this.action = clean(fields.action());
        this.result = clean(fields.result());
        this.metrics = clean(fields.metrics());
        this.tags = tags(fields.tags());
        this.updatedAt = now;
    }

    /** Every placeholder still in the story, in order, without its brackets. */
    public List<String> gaps() {
        var found = new ArrayList<String>();
        for (var text : List.of(title, situation, task, action, result, metrics)) {
            var matcher = PLACEHOLDER.matcher(text);
            while (matcher.find()) {
                var gap = matcher.group(1).strip();
                if (!gap.isEmpty() && !found.contains(gap)) found.add(gap);
            }
        }
        return found;
    }

    /** Lower case, trimmed, no blanks or repeats: "Leadership " and "leadership" are one tag. */
    static List<String> tags(List<String> tags) {
        if (tags == null) return new ArrayList<>();
        return new ArrayList<>(tags.stream()
                .filter(Objects::nonNull)
                .map(tag -> tag.strip().toLowerCase(Locale.ROOT))
                .filter(tag -> !tag.isEmpty())
                .distinct()
                .toList());
    }

    private static String clean(String text) {
        return text == null ? "" : text.strip();
    }

    /** What the user edits; null text counts as empty. */
    public record Fields(String title, String situation, String task, String action, String result,
                         String metrics, List<String> tags) {}

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getTitle() { return title; }
    public String getSituation() { return situation; }
    public String getTask() { return task; }
    public String getAction() { return action; }
    public String getResult() { return result; }
    public String getMetrics() { return metrics; }
    public List<String> getTags() { return List.copyOf(tags); }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
