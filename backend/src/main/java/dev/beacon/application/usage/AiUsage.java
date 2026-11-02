package dev.beacon.application.usage;

/**
 * The tokens one AI call used, as the provider reported them.
 *
 * @param provider          the provider's id in the {@link dev.beacon.application.providers.ProviderCatalog}
 * @param model             the model that answered, used to look up its price
 * @param inputTokens       every input token, cached ones included
 * @param cachedInputTokens how many of the input tokens were read from the provider's cache
 * @param outputTokens      output tokens, reasoning included
 */
public record AiUsage(String provider, String model, long inputTokens, long cachedInputTokens, long outputTokens) {

    public AiUsage {
        cachedInputTokens = Math.min(Math.max(cachedInputTokens, 0), Math.max(inputTokens, 0));
    }
}
