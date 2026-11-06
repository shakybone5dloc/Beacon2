package dev.beacon.contracts.stats;

import jakarta.annotation.Nullable;

/**
 * How long applications spend in one stage.
 *
 * @param moved       how many have left the stage, which the times are measured from
 * @param current     how many are in it now
 * @param averageDays null until one has left the stage
 * @param medianDays  null until one has left the stage
 */
public record StageTimeResponse(String stage, long moved, long current, @Nullable Double averageDays,
                                @Nullable Double medianDays) {}
