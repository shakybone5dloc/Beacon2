package dev.beacon.contracts.postings;

/** How one source fared in a search; error is null on success. */
public record SourceResultResponse(
        String id,
        int count,
        String error
) {
}
