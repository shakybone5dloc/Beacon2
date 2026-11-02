package dev.beacon.domain.searches;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One run of a saved search: what it gathered, what was new, what got scored. */
@Entity
@Table(name = "search_runs")
public class SearchRun {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID savedSearchId;

    @Column(nullable = false, updatable = false)
    private Instant startedAt;

    private Instant finishedAt;

    @Column(nullable = false)
    private int gathered;

    @Column(nullable = false)
    private int newMatches;

    @Column(nullable = false)
    private int scored;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> notes = new ArrayList<>();

    protected SearchRun() { }

    public static SearchRun start(UUID savedSearchId, Instant now) {
        var run = new SearchRun();
        run.id = UUID.randomUUID();
        run.savedSearchId = savedSearchId;
        run.startedAt = now;
        return run;
    }

    public void gathered(int count) { gathered = count; }
    public void foundNew(int count) { newMatches = count; }
    public void scoredOne() { scored++; }

    public void note(String line) {
        notes = new ArrayList<>(notes);
        notes.add(line);
    }

    public void finish(Instant now) { finishedAt = now; }

    public UUID getId() { return id; }
    public UUID getSavedSearchId() { return savedSearchId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public int getGathered() { return gathered; }
    public int getNewMatches() { return newMatches; }
    public int getScored() { return scored; }
    public List<String> getNotes() { return List.copyOf(notes); }
}
