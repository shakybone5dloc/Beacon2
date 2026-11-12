package dev.beacon.application.checkin;

import dev.beacon.application.usage.AiUsageMeter;

import java.util.List;

/**
 * Drafts a short "any update on my application?" email with one AI provider, using the key of
 * the user asking. Implementations throw {@link CheckInException} on failure.
 */
public interface CheckInWriter {

    /** The provider's id in the {@link dev.beacon.application.providers.ProviderCatalog}, e.g. "openai". */
    String provider();

    /** Reports the tokens used to {@code meter} as soon as the response arrives. */
    Email write(Input input, String apiKey, AiUsageMeter meter);

    /**
     * @param company   null when the employer isn't disclosed
     * @param recipient who it's addressed to: a person's name, or a team like "Hiring team"
     * @param sender    the user's name, to sign with; null when they haven't given one
     * @param history   what has happened so far, oldest first, one line each with its date
     * @param starting  the text in the form now, to improve on instead of starting over; may be blank
     */
    record Input(String company, String role, String status, String recipient, String sender,
                 List<String> history, String starting) {}

    record Email(String subject, String body) {}
}
