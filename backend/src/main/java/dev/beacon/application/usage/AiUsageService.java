package dev.beacon.application.usage;

import dev.beacon.contracts.usage.AiUsageResponse;
import dev.beacon.contracts.usage.AiUsageResponse.AiUsageLineResponse;
import dev.beacon.contracts.usage.AiUsageResponse.AiUsageMonthResponse;
import dev.beacon.domain.usage.AiUsageEntry;
import dev.beacon.infrastructure.persistence.AiUsageRepository;
import dev.beacon.infrastructure.persistence.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

/** Keeps a row for every AI call and adds them up into what each user spent. */
@Service
public class AiUsageService {

    private static final int MONTHS_SHOWN = 6;

    private final AiUsageRepository usage;
    private final UserAccountRepository users;
    private final AiPrices prices;
    private final Clock clock;

    public AiUsageService(AiUsageRepository usage, UserAccountRepository users, AiPrices prices, Clock clock) {
        this.usage = usage;
        this.users = users;
        this.prices = prices;
        this.clock = clock;
    }

    /**
     * Records one call. Runs in its own transaction, so the call is counted even when
     * the work that asked for it fails afterwards: the provider has billed it either way.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID ownerId, AiFeature feature, AiUsage call) {
        var now = Instant.now(clock);
        var cost = prices.cost(call, now.atZone(clock.getZone()).toLocalDate()).orElse(null);
        usage.save(AiUsageEntry.create(ownerId, feature.name(), call.provider(), call.model(), call.inputTokens(),
                call.cachedInputTokens(), call.outputTokens(), cost, now));
    }

    /** This month's spending in the user's time zone, by feature and by model, with the months before it. */
    @Transactional(readOnly = true)
    public AiUsageResponse summary(UUID ownerId, ZoneId zone) {
        var month = YearMonth.now(clock.withZone(zone));
        var from = month.atDay(1).atStartOfDay(zone).toInstant();
        var to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();

        var featureRows = usage.totalsByFeature(ownerId, from, to);
        var byFeature = lines(featureRows);
        var byModel = lines(usage.totalsByModel(ownerId, from, to));
        var total = byFeature.stream().map(AiUsageLineResponse::costUsd).reduce(BigDecimal.ZERO, BigDecimal::add);
        var calls = byFeature.stream().mapToLong(AiUsageLineResponse::calls).sum();
        var unpriced = featureRows.stream().mapToLong(row -> asLong(row[5])).sum();
        var budget = users.findById(ownerId).map(user -> user.getAiMonthlyBudgetUsd()).orElse(null);

        return new AiUsageResponse(month.toString(), total, calls, unpriced, budget, byFeature, byModel,
                recentMonths(ownerId, zone, month));
    }

    /**
     * True when the user has a budget and this month's spending has reached it. Work Beacon
     * starts on its own (like a saved search's scoring) checks this; work the user asks for doesn't.
     */
    @Transactional(readOnly = true)
    public boolean isOverBudget(UUID ownerId, ZoneId zone) {
        var budget = users.findById(ownerId).map(user -> user.getAiMonthlyBudgetUsd()).orElse(null);
        if (budget == null) return false;
        var month = YearMonth.now(clock.withZone(zone));
        var spent = usage.totalsByFeature(ownerId, month.atDay(1).atStartOfDay(zone).toInstant(),
                        month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()).stream()
                .map(row -> money(row[4]))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return spent.compareTo(budget) >= 0;
    }

    /** @param monthlyUsd null removes the budget */
    @Transactional
    public void setBudget(UUID ownerId, BigDecimal monthlyUsd) {
        users.findById(ownerId).orElseThrow().setAiMonthlyBudget(monthlyUsd);
    }

    /** Empty months are filled in, so the list always covers the same span. */
    private List<AiUsageMonthResponse> recentMonths(UUID ownerId, ZoneId zone, YearMonth current) {
        var first = current.minusMonths(MONTHS_SHOWN - 1);
        var from = first.atDay(1).atStartOfDay(zone).toInstant();
        var found = new HashMap<String, AiUsageMonthResponse>();
        for (var row : usage.monthlyTotals(ownerId, from, zone.getId())) {
            found.put((String) row[0], new AiUsageMonthResponse((String) row[0], asLong(row[2]), money(row[1])));
        }
        return IntStream.range(0, MONTHS_SHOWN)
                .mapToObj(i -> first.plusMonths(i).toString())
                .map(m -> found.getOrDefault(m, new AiUsageMonthResponse(m, 0, BigDecimal.ZERO)))
                .toList();
    }

    /** Most expensive first; ties (and unpriced lines) by name. */
    private static List<AiUsageLineResponse> lines(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new AiUsageLineResponse((String) row[0], asLong(row[1]), asLong(row[2]), asLong(row[3]), money(row[4])))
                .sorted(Comparator.comparing(AiUsageLineResponse::costUsd).reversed().thenComparing(AiUsageLineResponse::name))
                .toList();
    }

    private static BigDecimal money(Object value) {
        return Optional.ofNullable(value).map(v -> v instanceof BigDecimal d ? d : new BigDecimal(v.toString()))
                .orElse(BigDecimal.ZERO);
    }

    private static long asLong(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }
}
