package dev.beacon.contracts.searches;

import jakarta.annotation.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A posting a saved search found. */
public record JobMatchResponse(
        UUID id,
        /** NEW, SAVED, DISMISSED or BELOW_MIN. */
        String status,
        @Nullable UUID searchId,
        @Nullable String searchName,
        /** The source id, like "remotive" or "watchlist". */
        String source,
        String title,
        @Nullable String company,
        @Nullable String location,
        boolean remote,
        String url,
        @Nullable Instant postedAt,
        @Nullable LocalDate closesOn,
        @Nullable Integer salaryMin,
        @Nullable Integer salaryMax,
        @Nullable String salaryCurrency,
        @Nullable String salaryPeriod,
        @Nullable String salaryText,
        @Nullable String snippet,
        /** 0 to 100, once fit-scored. */
        @Nullable Integer score,
        /** The application it was saved as. */
        @Nullable UUID applicationId,
        Instant foundAt
) {}
