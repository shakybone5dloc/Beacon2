package dev.beacon.application.briefs;

import dev.beacon.application.briefs.BriefWriter.BriefInput;
import dev.beacon.application.usage.AiUsageMeter;

/**
 * Researches the employer on the web for the brief's company section, with one AI provider
 * and the key of the user asking. Implementations throw {@link BriefException} on failure.
 */
public interface CompanyResearcher {

    /** The provider's id in the {@link dev.beacon.application.providers.ProviderCatalog}, e.g. "openai". */
    String provider();

    String model();

    /**
     * Markdown with a numbered link after each sourced statement and the sources listed at the
     * end. Throws when nothing could be cited, so unsourced claims never reach the brief.
     * Reports the tokens and searches used to {@code meter} as each response arrives.
     */
    String research(BriefInput input, String apiKey, AiUsageMeter meter);
}
