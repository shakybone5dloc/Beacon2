package dev.beacon.infrastructure.persistence;

import dev.beacon.domain.usage.AiUsageEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AiUsageRepository extends JpaRepository<AiUsageEntry, UUID> {

    /**
     * Totals per feature between two instants:
     * [feature, calls, input tokens, output tokens, cost (null when nothing was priced), unpriced calls].
     */
    @Query("""
            select e.feature, count(e), sum(e.inputTokens), sum(e.outputTokens), sum(e.costUsd),
                   sum(case when e.costUsd is null then 1 else 0 end)
            from AiUsageEntry e
            where e.ownerId = :ownerId and e.createdAt >= :from and e.createdAt < :to
            group by e.feature
            """)
    List<Object[]> totalsByFeature(UUID ownerId, Instant from, Instant to);

    /** Totals per model between two instants, in the same shape as {@link #totalsByFeature}. */
    @Query("""
            select e.model, count(e), sum(e.inputTokens), sum(e.outputTokens), sum(e.costUsd),
                   sum(case when e.costUsd is null then 1 else 0 end)
            from AiUsageEntry e
            where e.ownerId = :ownerId and e.createdAt >= :from and e.createdAt < :to
            group by e.model
            """)
    List<Object[]> totalsByModel(UUID ownerId, Instant from, Instant to);

    /**
     * Spending per calendar month in a time zone, from an instant on: [month as "2026-10", cost, calls].
     * Months with no calls are left out.
     */
    @Query(nativeQuery = true, value = """
            select to_char(date_trunc('month', created_at at time zone :zone), 'YYYY-MM') as month,
                   coalesce(sum(cost_usd), 0) as cost, count(*) as calls
            from ai_usage
            where owner_id = :ownerId and created_at >= :from
            group by 1
            order by 1
            """)
    List<Object[]> monthlyTotals(UUID ownerId, Instant from, String zone);
}
