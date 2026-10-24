package dev.beacon.contracts.applications;

import java.time.Instant;

public record StatusChangeResponse(
        String fromStatus,
        String toStatus,
        Instant changedAt
) {
}
