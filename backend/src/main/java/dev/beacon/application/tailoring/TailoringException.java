package dev.beacon.application.tailoring;

/**
 * Resume suggestions could not be made. {@code upstream} separates "the AI service failed" (502)
 * from "this request can't work" (422), such as no job description to tailor to.
 */
public class TailoringException extends RuntimeException {

    private final boolean upstream;

    public TailoringException(String message, boolean upstream) {
        super(message);
        this.upstream = upstream;
    }

    public boolean isUpstream() {
        return upstream;
    }
}
