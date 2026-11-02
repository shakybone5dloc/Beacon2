package dev.beacon.application.coverletters;

import dev.beacon.application.usage.AiUsageMeter;

/**
 * Drafts cover letters with one AI provider, using the key of the user asking.
 * Implementations throw {@link CoverLetterException} on failure.
 */
public interface CoverLetterWriter {

    /** The provider's id in the {@link dev.beacon.application.providers.ProviderCatalog}, e.g. "openai". */
    String provider();

    /** Reports the tokens used to {@code meter} as soon as the response arrives, even if it then fails. */
    String draft(CoverLetterInput input, String apiKey, AiUsageMeter meter);

    /**
     * @param company      null when the employer isn't disclosed
     * @param instructions optional guidance from the user, e.g. "mention my move to Denver"
     */
    record CoverLetterInput(String company, String role, String jobDescription, String resume, String instructions) {}
}
