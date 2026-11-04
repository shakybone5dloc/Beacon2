package dev.beacon.application.briefs;

/**
 * A brief section could not be written. {@code upstream} separates "the AI service failed"
 * (502) from "this request can't work" (422), such as an application with no job description.
 */
public class BriefException extends RuntimeException {

    private final boolean upstream;

    public BriefException(String message, boolean upstream) {
        super(message);
        this.upstream = upstream;
    }

    public boolean isUpstream() {
        return upstream;
    }

    /** The section has the user's own edits, and the request didn't say to replace them (409). */
    public static class EditedException extends RuntimeException {
        public EditedException() {
            super("You've edited this section. Rewriting it replaces your changes.");
        }
    }
}
