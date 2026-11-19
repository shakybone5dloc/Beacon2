package dev.beacon.contracts.searches;

import jakarta.annotation.Nullable;

/** The full text of a match's posting, for reading it before saving it. */
public record MatchPostingResponse(
        /** Plain text, paragraphs separated by blank lines; null when the source gave only a snippet. */
        @Nullable String description
) {}
