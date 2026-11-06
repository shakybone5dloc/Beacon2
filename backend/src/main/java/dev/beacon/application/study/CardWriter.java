package dev.beacon.application.study;

import dev.beacon.application.usage.AiUsageMeter;

import java.util.List;

/**
 * Turns an interview brief into study cards with one AI provider, using the key of the user
 * asking. Implementations throw {@link StudyException} on failure.
 */
public interface CardWriter {

    /** The provider's id in the {@link dev.beacon.application.providers.ProviderCatalog}, e.g. "openai". */
    String provider();

    /** Reports the tokens used to {@code meter} as soon as the response arrives. */
    List<CardDraft> write(CardInput input, String apiKey, AiUsageMeter meter);

    /**
     * @param company  null when the employer isn't disclosed
     * @param sections the brief's written sections, by title, in reading order
     */
    record CardInput(String company, String role, List<Section> sections) {}

    record Section(String title, String markdown) {}

    record CardDraft(String question, String answer) {}
}
