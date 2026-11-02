package dev.beacon.contracts.fit;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** A job search result, not saved yet. */
public record ScorePostingFitRequest(
        /** A resume kept in the library. */
        @NotNull UUID resumeDocumentId,
        @NotBlank @Size(max = 200) String title,
        @Nullable @Size(max = 200) String company,
        /** The posting's text: its full description, or its snippet when that's all the source gave. */
        @NotBlank @Size(max = 60_000) String description
) {
}
