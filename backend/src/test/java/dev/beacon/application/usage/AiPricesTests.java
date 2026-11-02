package dev.beacon.application.usage;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiPricesTests {

    private static final AiPrices PRICES = new AiPrices(List.of(
            new AiPrices.Price("gpt-6-sol", LocalDate.of(2026, 1, 1),
                    new BigDecimal("2.00"), new BigDecimal("0.20"), new BigDecimal("10.00")),
            // A price cut from December 1.
            new AiPrices.Price("gpt-6-sol", LocalDate.of(2026, 12, 1),
                    new BigDecimal("1.00"), null, new BigDecimal("5.00"))));

    private static final AiUsage CALL = new AiUsage("openai", "gpt-6-sol", 1_000_000, 500_000, 100_000);

    @Test
    void pricesACallWithTheRatesInEffectThatDay() {
        // 500k uncached at $2 + 500k cached at $0.20 + 100k output at $10.
        assertThat(PRICES.cost(CALL, LocalDate.of(2026, 11, 30))).contains(new BigDecimal("2.100000"));
        // After the cut, with no cached rate given, cached input costs the same as plain input.
        assertThat(PRICES.cost(CALL, LocalDate.of(2026, 12, 1))).contains(new BigDecimal("1.500000"));
    }

    @Test
    void aModelWithNoPriceOrACallBeforeAnyPriceIsLeftUnpriced() {
        assertThat(PRICES.cost(new AiUsage("openai", "gpt-7", 10, 0, 10), LocalDate.of(2026, 11, 1))).isEmpty();
        assertThat(PRICES.cost(CALL, LocalDate.of(2025, 12, 31))).isEmpty();
    }

    @Test
    void cachedTokensNeverExceedTheInput() {
        assertThat(new AiUsage("openai", "gpt-6-sol", 100, 500, 0).cachedInputTokens()).isEqualTo(100);
    }
}
