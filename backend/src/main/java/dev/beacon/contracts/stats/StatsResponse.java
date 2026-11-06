package dev.beacon.contracts.stats;

import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * The job search in numbers (A3).
 *
 * @param since    applications created from this moment; null for all of them
 * @param bySource best performing first; applications with no source are "Not recorded"
 * @param byResume by the resume the application's fit was checked against
 * @param weeks    the last twelve weeks, oldest first, including empty ones; not limited by {@code since}
 */
public record StatsResponse(@Nullable Instant since, StatsGroupResponse overall, List<StatsGroupResponse> bySource,
                            List<StatsGroupResponse> byResume, List<StageTimeResponse> stages,
                            List<WeekCountResponse> weeks) {}
