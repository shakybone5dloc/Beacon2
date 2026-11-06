package dev.beacon.application.study;

/**
 * A study request that can't work as asked (422), or, with {@code upstream}, cards the AI
 * failed to make (502).
 */
public class StudyException extends RuntimeException {

    private final boolean upstream;

    public StudyException(String message) {
        this(message, false);
    }

    public StudyException(String message, boolean upstream) {
        super(message);
        this.upstream = upstream;
    }

    public boolean isUpstream() {
        return upstream;
    }
}
