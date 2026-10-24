package dev.beacon.domain.documents;

public enum DocumentKind {
    /** A saved copy of the job ad. */
    POSTING,
    RESUME,
    COVER_LETTER,
    /** Anything else an application asked for: transcripts, portfolios, references... */
    OTHER
}
