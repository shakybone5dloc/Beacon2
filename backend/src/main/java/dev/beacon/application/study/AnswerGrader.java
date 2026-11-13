package dev.beacon.application.study;

import dev.beacon.application.usage.AiUsageMeter;
import dev.beacon.domain.study.Grade;

/**
 * Compares a typed or spoken answer with a card's answer and suggests a grade, with one AI
 * provider and the key of the user asking. Implementations throw {@link StudyException} on
 * failure.
 */
public interface AnswerGrader {

    /** The provider's id in the {@link dev.beacon.application.providers.ProviderCatalog}, e.g. "openai". */
    String provider();

    /** Reports the tokens used to {@code meter} as soon as the response arrives. */
    Verdict grade(Input input, String apiKey, AiUsageMeter meter);

    /** @param expected the card's answer (Markdown); @param given what the user typed or said */
    record Input(String question, String expected, String given) {}

    record Verdict(Grade grade, String feedback, String tighter) {}
}
