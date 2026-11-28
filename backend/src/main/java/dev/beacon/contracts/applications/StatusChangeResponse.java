package dev.beacon.contracts.applications;

import dev.beacon.domain.applications.ApplicationStatus;
import dev.beacon.domain.applications.StatusChangeSource;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

import java.time.Instant;

public record StatusChangeResponse(
        Long id,
        @Schema(implementation = ApplicationStatus.class)
        @Nullable String fromStatus,
        @Schema(implementation = ApplicationStatus.class)
        String toStatus,
        Instant changedAt,
        @Schema(implementation = StatusChangeSource.class)
        String source,
        @Nullable String sourceEmailId,
        @Nullable Long revertsChangeId
) {
}
