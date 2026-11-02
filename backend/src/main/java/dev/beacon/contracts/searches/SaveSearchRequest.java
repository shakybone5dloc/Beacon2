package dev.beacon.contracts.searches;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Creates a saved search, or replaces one's settings (with the version it was loaded at). */
public record SaveSearchRequest(
        @NotBlank @Size(max = 100) String name,
        /** Every word must appear in the posting, as in Find jobs. */
        @NotBlank @Size(max = 500) String keywords,
        /** A posting whose title has any of these words or phrases is dropped. */
        @Nullable @Size(max = 20) List<@NotBlank @Size(max = 60) String> excludeWords,
        @Nullable @Size(max = 200) String location,
        @Nullable Boolean remoteOnly,
        /** Yearly pay floor; postings that don't state pay are kept. */
        @Nullable @PositiveOrZero Integer salaryMin,
        /** Source ids; empty or left out means every source you can search. */
        @Nullable @Size(max = 30) List<@NotBlank String> sources,
        /** Only postings this recent; 7 when left out. */
        @Nullable @Min(1) @Max(60) Integer postedWithinDays,
        /** A library resume to fit-score new matches against; no resume, no scoring. */
        @Nullable UUID resumeDocumentId,
        /** Matches scored below this are kept out of sight. */
        @Nullable @Min(0) @Max(100) Integer minScore,
        /** True when left out. */
        @Nullable Boolean active,
        @NotEmpty Set<DayOfWeek> runDays,
        @NotNull @Min(0) @Max(23) Integer runHour,
        /** An IANA time zone like "America/New_York"; the run hour is in it. */
        @NotBlank @Size(max = 64) String timeZone,
        /** Required when editing: the version the search was loaded at. */
        @Nullable Long version
) {}
