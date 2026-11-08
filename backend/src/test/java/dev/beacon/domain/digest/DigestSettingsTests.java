package dev.beacon.domain.digest;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DigestSettingsTests {

    private static final ZoneId CHICAGO = ZoneId.of("America/Chicago");
    /** Monday Nov 9 2026, 8:30 AM in Chicago. */
    private static final Instant MONDAY_830 = Instant.parse("2026-11-09T14:30:00Z");

    private DigestSettings mondaysAt8() {
        var s = DigestSettings.defaults(UUID.randomUUID(), CHICAGO, MONDAY_830);
        s.change(true, DayOfWeek.MONDAY, 8, CHICAGO, MONDAY_830);
        return s;
    }

    @Test
    void isDueOnceItsLocalHourHasPassedOnItsDay() {
        var s = mondaysAt8();

        assertThat(s.isDue(MONDAY_830)).isTrue();
        assertThat(s.isDue(Instant.parse("2026-11-09T13:59:00Z"))).as("7:59 Chicago").isFalse();
        assertThat(s.isDue(Instant.parse("2026-11-10T14:30:00Z"))).as("Tuesday").isFalse();
    }

    @Test
    void goesOutOncePerWeek() {
        var s = mondaysAt8();
        s.sent(MONDAY_830);

        assertThat(s.isDue(MONDAY_830.plusSeconds(3600))).isFalse();
        assertThat(s.isDue(MONDAY_830.plusSeconds(7 * 86_400))).isTrue();
    }

    @Test
    void isNeverDueWhileOff() {
        var s = mondaysAt8();
        s.change(false, DayOfWeek.MONDAY, 8, CHICAGO, MONDAY_830);

        assertThat(s.isDue(MONDAY_830)).isFalse();
        assertThat(s.nextSendAt(MONDAY_830)).isNull();
    }

    @Test
    void saysWhenItGoesOutNext() {
        var s = mondaysAt8();

        // Past 8 on Monday, so next Monday.
        assertThat(s.nextSendAt(MONDAY_830).toLocalDateTime()).hasToString("2026-11-16T08:00");
        assertThat(s.nextSendAt(Instant.parse("2026-11-09T13:00:00Z")).toLocalDateTime()).hasToString("2026-11-09T08:00");
        assertThat(s.nextSendAt(Instant.parse("2026-11-12T00:00:00Z")).toLocalDateTime()).hasToString("2026-11-16T08:00");
    }
}
