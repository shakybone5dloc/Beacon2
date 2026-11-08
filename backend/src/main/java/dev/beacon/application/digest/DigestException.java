package dev.beacon.application.digest;

/**
 * The digest couldn't be sent. {@code upstream} separates "the mail server failed" (502)
 * from "this can't work here" (422), such as no mail server set up.
 */
public class DigestException extends RuntimeException {

    private final boolean upstream;

    public DigestException(String message, boolean upstream) {
        super(message);
        this.upstream = upstream;
    }

    public boolean isUpstream() {
        return upstream;
    }
}
