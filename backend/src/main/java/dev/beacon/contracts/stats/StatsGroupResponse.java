package dev.beacon.contracts.stats;

import jakarta.annotation.Nullable;

/**
 * How a group of applications did: all of them, one source or one resume. Each count is
 * of applications that ever reached that point, whatever they're at now.
 *
 * @param name                  the source or resume; null for all applications
 * @param applied               sent: reached Applied or anything after it
 * @param responded             heard back: an interview, an offer or a rejection
 * @param interviewed           reached Interviewing, or an offer
 * @param offered               reached Offer
 * @param ghosted               still Applied with no word for three weeks or more
 * @param medianDaysToResponse  from applying to hearing back, for those that did; null when none have
 */
public record StatsGroupResponse(@Nullable String name, long total, long applied, long responded, long interviewed,
                                 long offered, long ghosted, @Nullable Double medianDaysToResponse) {}
