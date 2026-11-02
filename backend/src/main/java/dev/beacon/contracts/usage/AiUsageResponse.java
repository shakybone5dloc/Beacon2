package dev.beacon.contracts.usage;

import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.util.List;

/**
 * This month's AI spending, in US dollars.
 *
 * @param month         the month shown, like "2026-11", in the time zone asked for
 * @param unpricedCalls calls on a model with no price on file, left out of the totals
 * @param recentMonths  this month and the five before it, oldest first, including empty months
 */
public record AiUsageResponse(
        String month,
        BigDecimal totalUsd,
        long calls,
        long unpricedCalls,
        @Nullable BigDecimal budgetUsd,
        List<AiUsageLineResponse> byFeature,
        List<AiUsageLineResponse> byModel,
        List<AiUsageMonthResponse> recentMonths) {

    /** @param name a feature such as "FIT_SCORE", or a model such as "gpt-6-sol" */
    public record AiUsageLineResponse(String name, long calls, long inputTokens, long outputTokens, BigDecimal costUsd) {}

    public record AiUsageMonthResponse(String month, long calls, BigDecimal costUsd) {}
}
