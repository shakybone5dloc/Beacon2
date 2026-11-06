package dev.beacon.contracts.stats;

import java.time.LocalDate;

/** Applications sent in the week starting on {@code weekStart} (a Monday, in the user's time zone). */
public record WeekCountResponse(LocalDate weekStart, long applied) {}
