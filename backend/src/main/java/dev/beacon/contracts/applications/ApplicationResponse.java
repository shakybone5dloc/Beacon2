package dev.beacon.contracts.applications;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.List;

public record ApplicationResponse (
        UUID id,
        String company,
        String role,
        String jobUrl,
        String notes,
        LocalDate applyBy,
        LocalDate followUpOn,
        String location,
        Boolean remote,
        Integer salaryMin,
        Integer salaryMax,
        String salaryCurrency,
        String salaryPeriod,
        String salaryText,
        String source,
        LocalDate postedOn,
        /** Null in list responses to keep the board light; present when fetching one application. */
        String jobDescription,
        /** When the application first moved to APPLIED; null if it never has. */
        Instant appliedAt,
        String status,
        Instant createdAt,
        Instant updatedAt,
        Instant statusChangedAt,
        Long version,
        List<String> allowedTransitions
) {
}
