package dev.beacon.contracts.postings;

public record JobSourceResponse(
        String id,
        String name,
        String coverage,
        String attributionUrl,
        boolean configured,
        String setupHint
) {
}
