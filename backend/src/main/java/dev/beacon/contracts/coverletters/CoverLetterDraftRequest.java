package dev.beacon.contracts.coverletters;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CoverLetterDraftRequest(
        /** A resume attached to the application or kept in the library. */
        @NotNull UUID resumeDocumentId,
        @Nullable @Size(max = 1000) String instructions
) {
}
