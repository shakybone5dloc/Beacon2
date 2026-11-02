package dev.beacon.domain.searches;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** A job search the user wants run for them on chosen days, collecting new postings as matches. */
@Entity
@Table(name = "saved_searches")
public class SavedSearch {

    /** A run that started longer ago than this is taken to have died, and may start again. */
    public static final Duration STALE_RUN = Duration.ofMinutes(30);

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 500)
    private String keywords;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> excludeWords;

    @Column(length = 200)
    private String location;

    @Column(nullable = false)
    private boolean remoteOnly;

    private Integer salaryMin;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> sources;

    @Column(nullable = false)
    private int postedWithinDays;

    private UUID resumeDocumentId;

    private Integer minScore;

    @Column(nullable = false)
    private boolean active;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> runDays;

    @Column(nullable = false)
    private int runHour;

    @Column(nullable = false, length = 64)
    private String timeZone;

    private Instant nextRunAt;

    private Instant lastRunAt;

    private Instant runningSince;

    @Version
    private long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected SavedSearch() { }

    /** What the user chose; the same shape for creating and editing. */
    public record Settings(String name, String keywords, List<String> excludeWords, String location, boolean remoteOnly,
                           Integer salaryMin, List<String> sources, int postedWithinDays, UUID resumeDocumentId,
                           Integer minScore, boolean active, Set<DayOfWeek> runDays, int runHour, ZoneId timeZone) {
        public Settings {
            if (runDays.isEmpty()) throw new IllegalArgumentException("Pick at least one day to run on");
            if (runHour < 0 || runHour > 23) throw new IllegalArgumentException("The hour must be 0 to 23");
            excludeWords = List.copyOf(excludeWords);
            sources = List.copyOf(sources);
            runDays = Set.copyOf(runDays);
        }
    }

    public static SavedSearch create(UUID ownerId, Settings settings, Instant now) {
        var search = new SavedSearch();
        search.id = UUID.randomUUID();
        search.ownerId = ownerId;
        search.createdAt = now;
        search.apply(settings, now);
        return search;
    }

    /** Changing the schedule moves the next run to fit it. */
    public void apply(Settings settings, Instant now) {
        name = settings.name().strip();
        keywords = settings.keywords().strip();
        excludeWords = settings.excludeWords();
        location = blankToNull(settings.location());
        remoteOnly = settings.remoteOnly();
        salaryMin = settings.salaryMin();
        sources = settings.sources();
        postedWithinDays = settings.postedWithinDays();
        resumeDocumentId = settings.resumeDocumentId();
        minScore = settings.minScore();
        active = settings.active();
        runDays = settings.runDays().stream().sorted().map(DayOfWeek::name).toList();
        runHour = settings.runHour();
        timeZone = settings.timeZone().getId();
        nextRunAt = active ? nextRunAfter(now) : null;
        updatedAt = now;
    }

    /** The first chosen day and hour after {@code now}, in the search's time zone. */
    public Instant nextRunAfter(Instant now) {
        var zone = ZoneId.of(timeZone);
        var today = now.atZone(zone).toLocalDate();
        var days = getRunDays();
        for (int ahead = 0; ahead <= 7; ahead++) {
            var date = today.plusDays(ahead);
            if (!days.contains(date.getDayOfWeek())) continue;
            var candidate = date.atTime(runHour, 0).atZone(zone).toInstant();
            if (candidate.isAfter(now)) return candidate;
        }
        throw new IllegalStateException("A saved search always has a day to run on");
    }

    public boolean isRunning(Instant now) {
        return runningSince != null && runningSince.isAfter(now.minus(STALE_RUN));
    }

    /** Marks a run as started and books the next one, so a scheduled run isn't picked up twice. */
    public void startRun(Instant now) {
        runningSince = now;
        lastRunAt = now;
        nextRunAt = active ? nextRunAfter(now) : null;
    }

    public void finishRun() {
        runningSince = null;
    }

    /** The resume was deleted, or the user picked none: matches stay unscored. */
    public void clearResume() {
        resumeDocumentId = null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public String getKeywords() { return keywords; }
    public List<String> getExcludeWords() { return excludeWords; }
    public String getLocation() { return location; }
    public boolean isRemoteOnly() { return remoteOnly; }
    public Integer getSalaryMin() { return salaryMin; }
    public List<String> getSources() { return sources; }
    public int getPostedWithinDays() { return postedWithinDays; }
    public UUID getResumeDocumentId() { return resumeDocumentId; }
    public Integer getMinScore() { return minScore; }
    public boolean isActive() { return active; }
    public Set<DayOfWeek> getRunDays() {
        var days = EnumSet.noneOf(DayOfWeek.class);
        runDays.forEach(day -> days.add(DayOfWeek.valueOf(day)));
        return days;
    }
    public int getRunHour() { return runHour; }
    public ZoneId getTimeZone() { return ZoneId.of(timeZone); }
    public Instant getNextRunAt() { return nextRunAt; }
    public Instant getLastRunAt() { return lastRunAt; }
    public Instant getRunningSince() { return runningSince; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
