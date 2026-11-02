package dev.beacon.application.fit;

/**
 * A fit could not be scored. {@code upstream} separates "the AI service failed"
 * (502) from "this request can't work" (422), such as a missing job description.
 */
public class FitScoreException extends RuntimeException {

    private final boolean upstream;

    public FitScoreException(String message, boolean upstream) {
        super(message);
        this.upstream = upstream;
    }

    public boolean isUpstream() {
        return upstream;
    }
}
