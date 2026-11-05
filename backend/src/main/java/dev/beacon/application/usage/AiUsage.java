package dev.beacon.application.usage;

/**
 * The tokens one AI call used, as the provider reported them.
 *
 * @param provider          the provider's id in the {@link dev.beacon.application.providers.ProviderCatalog}
 * @param model             the model that answered, used to look up its price
 * @param inputTokens       every input token, cached ones included
 * @param cachedInputTokens how many of the input tokens were read from the provider's cache
 * @param outputTokens      output tokens, reasoning included
 * @param webSearches       web searches the provider ran for the call, each charged on top of the tokens
 */
public record AiUsage(String provider, String model, long inputTokens, long cachedInputTokens, long outputTokens,
                      long webSearches) {

    public AiUsage {
        cachedInputTokens = Math.min(Math.max(cachedInputTokens, 0), Math.max(inputTokens, 0));
        webSearches = Math.max(webSearches, 0);
    }

    /** A call that searched nothing, which is most of them. */
    public AiUsage(String provider, String model, long inputTokens, long cachedInputTokens, long outputTokens) {
        this(provider, model, inputTokens, cachedInputTokens, outputTokens, 0);
    }
}
