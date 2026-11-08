package dev.beacon.application.digest;

/** Sends email from Beacon. Implementations throw {@link DigestException} when sending fails. */
public interface Mailer {

    /** False when no mail server is set up, so nothing can be sent. */
    boolean isConfigured();

    void send(String to, String subject, String html, String text);
}
