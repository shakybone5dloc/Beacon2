package dev.beacon.contracts.postings;

/** A source the signed-in user can search. */
public record JobSourceResponse(
        String id,
        String name,
        String coverage,
        String attributionUrl,
        /** True when searching it uses a key the user saved. */
        boolean usesYourKey
) {
}
