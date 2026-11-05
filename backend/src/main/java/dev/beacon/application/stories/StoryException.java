package dev.beacon.application.stories;

/**
 * Stories could not be drafted. {@code upstream} separates "the AI service failed" (502)
 * from "this request can't work" (422), such as no notes and no resume to draw on.
 */
public class StoryException extends RuntimeException {

    private final boolean upstream;

    public StoryException(String message, boolean upstream) {
        super(message);
        this.upstream = upstream;
    }

    public boolean isUpstream() {
        return upstream;
    }
}
