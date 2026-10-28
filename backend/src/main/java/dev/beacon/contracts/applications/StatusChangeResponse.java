package dev.beacon.contracts.applications;

import dev.beacon.domain.applications.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record StatusChangeResponse(
        @Schema(implementation = ApplicationStatus.class)
        String fromStatus,
        @Schema(implementation = ApplicationStatus.class)
        String toStatus,
        Instant changedAt
) {
}
