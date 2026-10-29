package dev.beacon.domain.interviews;

/** How an interview went; an interview without one hasn't happened or hasn't been heard back on. */
public enum InterviewOutcome {
    PASSED,
    DID_NOT_PASS,
    /** Never happened; it doesn't count as the next or last interview. */
    CANCELLED
}
