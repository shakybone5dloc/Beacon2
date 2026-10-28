package dev.beacon.contracts.postings;

import jakarta.annotation.Nullable;

/** How one source fared in a search; error is null on success. */
public record SourceResultResponse(
        String id,
        int count,
        @Nullable String error
) {
}
