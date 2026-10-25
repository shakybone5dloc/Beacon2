package dev.beacon.contracts.postings;

public record JobSourceResponse(
        String id,
        String name,
        String coverage,
        String attributionUrl,
        /** False when switched off by configuration (BEACON_DISABLED_SOURCES). */
        boolean enabled,
        /** False when the source needs credentials that are not set. */
        boolean configured,
        String setupHint
) {
}
