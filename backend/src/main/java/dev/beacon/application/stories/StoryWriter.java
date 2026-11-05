package dev.beacon.application.stories;

import dev.beacon.application.usage.AiUsageMeter;

import java.util.List;

/**
 * Drafts STAR stories with one AI provider, using the key of the user asking.
 * Implementations throw {@link StoryException} on failure.
 */
public interface StoryWriter {

    /** The provider's id in the {@link dev.beacon.application.providers.ProviderCatalog}, e.g. "openai". */
    String provider();

    /** Reports the tokens used to {@code meter} as soon as the response arrives. */
    List<Draft> draft(DraftInput input, String apiKey, AiUsageMeter meter);

    /**
     * @param notes  the user's rough notes on one story; null to suggest a few from the resume
     * @param resume the resume's text; null when the user has none Beacon can read
     */
    record DraftInput(String notes, String resume) {}

    record Draft(String title, String situation, String task, String action, String result, String metrics,
                 List<String> tags) {}
}
