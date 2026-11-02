package dev.beacon.contracts.fit;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ScoreApplicationFitRequest(
        /** A resume attached to the application or kept in the library. */
        @NotNull UUID resumeDocumentId
) {
}
