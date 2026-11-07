package dev.beacon.application.tailoring;

import dev.beacon.application.usage.AiUsageMeter;
import dev.beacon.domain.tailoring.ResumeSuggestion;

import java.util.List;

/**
 * Suggests edits that tailor a resume to one posting, with one AI provider and the key of
 * the user asking. Implementations throw {@link TailoringException} on failure.
 */
public interface ResumeTailor {

    /** The provider's id in the {@link dev.beacon.application.providers.ProviderCatalog}, e.g. "openai". */
    String provider();

    /** The model it asks, recorded with each suggestion. */
    String model();

    /** Reports the tokens used to {@code meter} as soon as the response arrives. */
    List<Suggestion> suggest(TailorInput input, String apiKey, AiUsageMeter meter);

    /**
     * @param company     null when the application has none
     * @param gaps        what the fit check found missing; empty when the fit wasn't checked with this resume
     * @param matched     what the fit check found the resume already shows
     */
    record TailorInput(String company, String role, String jobDescription, String resume,
                       List<String> gaps, List<String> matched) {}

    /** @param example wording to paste in; null when the change isn't a rewrite */
    record Suggestion(ResumeSuggestion.Kind kind, String section, String suggestion, String reason, String example) {}
}
