package dev.beacon.contracts.postings;

import jakarta.annotation.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record JobPostingResponse(
        String source,
        String id,
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
        List<String> tags,
        @Nullable String snippet,
        @Nullable String description
) {
}
