package dev.beacon.domain.applications;

import java.time.LocalDate;

/**
 * Facts from the job ad worth keeping after the posting comes down. Every
 * field is optional; blank text becomes null.
 *
 * @param salaryPeriod "year", "month", "week", "day" or "hour"
 * @param salaryText   free-text pay for ads without numbers, e.g. "$1000 per task"
 * @param source       where it was found: a job board, "Referral", "Company site"...
 */
public record PostingDetails(
        String location,
        Boolean remote,
        Integer salaryMin,
        Integer salaryMax,
        String salaryCurrency,
        String salaryPeriod,
        String salaryText,
        String source,
        LocalDate postedOn
) {

    public static final PostingDetails NONE = new PostingDetails(null, null, null, null, null, null, null, null, null);

    public PostingDetails {
        if (salaryMin != null && salaryMax != null && salaryMin > salaryMax) {
            throw new IllegalArgumentException("salaryMin is above salaryMax");
        }
        location = trimToNull(location);
        salaryCurrency = salaryCurrency == null ? null : trimToNull(salaryCurrency.toUpperCase());
        salaryPeriod = trimToNull(salaryPeriod);
        salaryText = trimToNull(salaryText);
        source = trimToNull(source);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
