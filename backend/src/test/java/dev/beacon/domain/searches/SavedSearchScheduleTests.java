package dev.beacon.domain.searches;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SavedSearchScheduleTests {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    private static SavedSearch search(Set<DayOfWeek> days, int hour, Instant now) {
        return SavedSearch.create(UUID.randomUUID(), new SavedSearch.Settings("Java", "java", List.of(), null,
                false, null, List.of(), 7, null, null, true, days, hour, NEW_YORK), now);
    }

    @Test
    void runsLaterTodayWhenTheHourHasntComeYet() {
        // Monday 2026-11-02, 6:00 in New York.
        var now = Instant.parse("2026-11-02T11:00:00Z");

        assertThat(search(Set.of(DayOfWeek.MONDAY), 7, now).getNextRunAt())
                .isEqualTo(Instant.parse("2026-11-02T12:00:00Z"));
    }

    @Test
    void skipsToTheNextChosenDayOnceTheHourHasPassed() {
        // Friday 2026-11-06, 8:00 in New York; weekdays only, so next is Monday.
        var now = Instant.parse("2026-11-06T13:00:00Z");
        var weekdays = Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

        assertThat(search(weekdays, 7, now).getNextRunAt()).isEqualTo(Instant.parse("2026-11-09T12:00:00Z"));
    }

    @Test
    void keepsTheLocalHourAcrossADaylightSavingChange() {
        // Saturday 2026-10-31 in New York (still daylight time); the next Monday is in standard time.
        var now = Instant.parse("2026-10-31T15:00:00Z");

        assertThat(search(Set.of(DayOfWeek.MONDAY), 7, now).getNextRunAt())
                .isEqualTo(Instant.parse("2026-11-02T12:00:00Z"));
    }

    @Test
    void aWeekAheadWhenTheOnlyDayIsTodayAndItsPassed() {
        var now = Instant.parse("2026-11-02T13:00:00Z"); // Monday, 8:00 in New York

        assertThat(search(Set.of(DayOfWeek.MONDAY), 7, now).getNextRunAt())
                .isEqualTo(Instant.parse("2026-11-09T12:00:00Z"));
    }

    @Test
    void aPausedSearchHasNoNextRunAndARunningOneGoesStale() {
        var now = Instant.parse("2026-11-02T11:00:00Z");
        var search = search(Set.of(DayOfWeek.MONDAY), 7, now);
        search.apply(new SavedSearch.Settings("Java", "java", List.of(), null, false, null, List.of(), 7, null, null,
                false, Set.of(DayOfWeek.MONDAY), 7, NEW_YORK), now);
        assertThat(search.getNextRunAt()).isNull();

        search.startRun(now);
        assertThat(search.isRunning(now.plusSeconds(60))).isTrue();
        assertThat(search.isRunning(now.plus(SavedSearch.STALE_RUN).plusSeconds(1))).isFalse();
    }
}
