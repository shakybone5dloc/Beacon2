package dev.beacon.application.checkin;

/**
 * A check-in email that can't be drafted as asked (422), or, with {@code upstream}, one the
 * AI failed to write (502).
 */
public class CheckInException extends RuntimeException {

    private final boolean upstream;

    public CheckInException(String message) {
        this(message, false);
    }

    public CheckInException(String message, boolean upstream) {
        super(message);
        this.upstream = upstream;
    }

    public boolean isUpstream() {
        return upstream;
    }
}
