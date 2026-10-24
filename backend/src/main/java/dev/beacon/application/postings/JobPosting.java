package dev.beacon.application.postings;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** A job ad from any source, normalized to one shape. Nullable fields are ones a source may not provide. */
public record JobPosting(
        String source,
        String id,
        String title,
        String company,
        String location,
        boolean remote,
        /** Where the user should go to view and apply; sources require their own link here. */
        String url,
        Instant postedAt,
        /** Last day to apply or the listing's end, when the source says. */
        LocalDate closesOn,
        Integer salaryMin,
        Integer salaryMax,
        String salaryCurrency,
        /** e.g. "year" or "hour"; null when unknown. */
        String salaryPeriod,
        /** Free-text salary for sources that don't give numbers, e.g. "70K-80K". */
        String salaryText,
        List<String> tags,
        /** Short plain-text excerpt. */
        String snippet,
        /** The full ad as plain text when the source provides it; null otherwise. */
        String description
) {
}
