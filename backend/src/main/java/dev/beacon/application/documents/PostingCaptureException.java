package dev.beacon.application.documents;

/**
 * Saving a page as PDF did not work. {@code upstream} separates "the page or
 * browser failed" (worth retrying, or printing by hand) from "this request
 * can't work", such as an application with no job URL.
 */
public class PostingCaptureException extends RuntimeException {

    private final boolean upstream;

    public PostingCaptureException(String message, boolean upstream) {
        super(message);
        this.upstream = upstream;
    }

    public boolean isUpstream() {
        return upstream;
    }
}
