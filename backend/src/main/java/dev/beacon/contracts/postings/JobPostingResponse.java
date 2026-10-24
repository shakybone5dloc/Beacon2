package dev.beacon.contracts.postings;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record JobPostingResponse(
        String source,
        String id,
        String title,
        String company,
        String location,
        boolean remote,
        String url,
        Instant postedAt,
        LocalDate closesOn,
        Integer salaryMin,
        Integer salaryMax,
        String salaryCurrency,
        String salaryPeriod,
        String salaryText,
        List<String> tags,
        String snippet
) {
}
