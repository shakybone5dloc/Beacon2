package dev.beacon.contracts.applications;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** A null date clears the follow-up. */
public record FollowUpRequest(
        @Nullable LocalDate followUpOn,
        @NotNull Long version
) {
}
