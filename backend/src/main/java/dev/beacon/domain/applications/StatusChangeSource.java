package dev.beacon.domain.applications;

/** Who caused a status change. */
public enum StatusChangeSource {
    /** The user moved the application themselves. */
    USER,
    /** An incoming email moved it (email integration, roadmap E3). */
    EMAIL
}
