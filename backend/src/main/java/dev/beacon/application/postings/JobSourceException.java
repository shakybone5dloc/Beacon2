package dev.beacon.application.postings;

/**
 * A failure the source explained itself, such as "Your account has run out of searches".
 * The message is shown to the user next to the source's name, so it must be fit to read.
 */
public class JobSourceException extends RuntimeException {

    public JobSourceException(String message) {
        super(message);
    }
}
