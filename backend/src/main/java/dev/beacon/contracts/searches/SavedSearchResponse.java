package dev.beacon.contracts.searches;

import jakarta.annotation.Nullable;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SavedSearchResponse(
        UUID id,
        String name,
        String keywords,
        List<String> excludeWords,
        @Nullable String location,
        boolean remoteOnly,
        @Nullable Integer salaryMin,
        List<String> sources,
        int postedWithinDays,
        @Nullable UUID resumeDocumentId,
        @Nullable Integer minScore,
        boolean active,
        List<DayOfWeek> runDays,
        int runHour,
        String timeZone,
        /** Null while the search is paused. */
        @Nullable Instant nextRunAt,
        @Nullable Instant lastRunAt,
        boolean running,
        /** Matches from this search still waiting for a decision. */
        long newMatches,
        @Nullable SearchRunResponse lastRun,
        long version,
        Instant createdAt
) {}
