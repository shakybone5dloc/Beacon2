package dev.beacon.application.users;

import java.time.Duration;

/** Sign-in or sign-up tried too often; answered with 429 and when to try again. */
public class TooManyAttemptsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyAttemptsException(Duration retryAfter) {
        super("Too many attempts. Try again in " + minutes(retryAfter) + ".");
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }

    private static String minutes(Duration wait) {
        long minutes = Math.max(1, (wait.toSeconds() + 59) / 60);
        return minutes == 1 ? "a minute" : minutes + " minutes";
    }
}
