package dev.beacon.application.coverletters;

/**
 * A cover letter could not be drafted or saved. {@code upstream} separates
 * "the AI service or renderer failed" (502) from "this request can't work"
 * (422), such as an application with no job description yet.
 */
public class CoverLetterException extends RuntimeException {

    private final boolean upstream;

    public CoverLetterException(String message, boolean upstream) {
        super(message);
        this.upstream = upstream;
    }

    public boolean isUpstream() {
        return upstream;
    }
}
