package dev.beacon.application.usage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * What each model costs, in US dollars per million tokens, from {@code beacon.ai.prices}.
 * Each price has a start date, so adding a new row when a provider changes its prices
 * keeps older calls priced as they were.
 */
@ConfigurationProperties("beacon.ai")
public record AiPrices(List<Price> prices) {

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    public AiPrices {
        prices = prices == null ? List.of() : List.copyOf(prices);
    }

    /** @param since the first day this price applies */
    public record Price(String model, LocalDate since, BigDecimal input, BigDecimal cachedInput, BigDecimal output) {
        public Price {
            cachedInput = cachedInput == null ? input : cachedInput;
        }
    }

    /** The price in effect for a model on a day, if one is on file. */
    public Optional<Price> priceOn(String model, LocalDate day) {
        return prices.stream()
                .filter(p -> p.model().equalsIgnoreCase(model) && !p.since().isAfter(day))
                .max(Comparator.comparing(Price::since));
    }

    /** What a call cost, to the millionth of a dollar; empty when the model has no price on file. */
    public Optional<BigDecimal> cost(AiUsage usage, LocalDate day) {
        return priceOn(usage.model(), day).map(price -> {
            var uncached = BigDecimal.valueOf(usage.inputTokens() - usage.cachedInputTokens()).multiply(price.input());
            var cached = BigDecimal.valueOf(usage.cachedInputTokens()).multiply(price.cachedInput());
            var output = BigDecimal.valueOf(usage.outputTokens()).multiply(price.output());
            return uncached.add(cached).add(output).divide(MILLION, 6, RoundingMode.HALF_UP);
        });
    }
}
