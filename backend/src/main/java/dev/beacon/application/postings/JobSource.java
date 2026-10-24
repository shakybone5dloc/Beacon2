package dev.beacon.application.postings;

import java.util.List;

/** A place job postings can be searched. Implementations live in infrastructure. */
public interface JobSource {

    /** Stable id used in requests, e.g. "remotive". */
    String id();

    String name();

    /** One line on what the source covers. */
    String coverage();

    /** Link credited next to every result; most free feeds require it. */
    String attributionUrl();

    /** False when the source needs credentials that are not set. */
    default boolean isConfigured() {
        return true;
    }

    /** How to configure the source when it is not; null when nothing is needed. */
    default String setupHint() {
        return null;
    }

    /** Throws if the source cannot be reached; the caller reports it per source. */
    List<JobPosting> search(PostingQuery query);
}
