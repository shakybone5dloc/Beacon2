package dev.beacon.contracts.applications;

import java.time.Instant;
import java.util.UUID;

public record ApplicationResponse (
        UUID id,
        String company,
        String role,
        String jobUrl,
        String notes,
        String status,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
}
