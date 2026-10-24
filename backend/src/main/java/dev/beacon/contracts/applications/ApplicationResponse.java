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
        String status,
        Instant createdAt,
        Instant updatedAt,
        Instant statusChangedAt,
        Long version,
        List<String> allowedTransitions
) {
}
