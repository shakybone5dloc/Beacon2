package dev.beacon.domain.study;

import dev.beacon.domain.study.Scheduler.Schedule;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulerTests {

    private static final Instant NOW = Instant.parse("2026-11-06T15:00:00Z");

    private static Schedule after(Schedule s, Grade... grades) {
        for (var grade : grades) s = Scheduler.next(s, grade, NOW);
        return s;
    }

    @Test
    void aKnownCardComesBackAfterOneDayThenThreeThenGrowsWithItsEase() {
        var s = Schedule.fresh(NOW);

        s = Scheduler.next(s, Grade.GOOD, NOW);
        assertThat(s.intervalDays()).isEqualTo(1);
        assertThat(s.dueAt()).isEqualTo(NOW.plus(Duration.ofDays(1)));

        s = Scheduler.next(s, Grade.GOOD, NOW);
        assertThat(s.intervalDays()).isEqualTo(3);

        s = Scheduler.next(s, Grade.GOOD, NOW);
        assertThat(s.intervalDays()).isEqualTo(8); // 3 x 2.5, rounded
        assertThat(s.repetitions()).isEqualTo(3);
        assertThat(s.ease()).isEqualTo(2.5);
    }

    @Test
    void aMissedCardComesBackInTheSameSittingAndCountsAsALapseOnlyOnceLearned() {
        var learned = after(Schedule.fresh(NOW), Grade.GOOD, Grade.GOOD);

        var missed = Scheduler.next(learned, Grade.AGAIN, NOW);

        assertThat(missed.dueAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(missed.intervalDays()).isZero();
        assertThat(missed.repetitions()).isZero();
        assertThat(missed.lapses()).isEqualTo(1);
        assertThat(missed.ease()).isEqualTo(2.3);
        // Missing a card never learned isn't a lapse.
        assertThat(Scheduler.next(Schedule.fresh(NOW), Grade.AGAIN, NOW).lapses()).isZero();
    }

    @Test
    void hardGrowsSlowlyAndEasyJumpsAhead() {
        var learned = after(Schedule.fresh(NOW), Grade.GOOD, Grade.GOOD); // 3 days

        var hard = Scheduler.next(learned, Grade.HARD, NOW);
        assertThat(hard.intervalDays()).isEqualTo(4);
        assertThat(hard.ease()).isEqualTo(2.35);

        var easy = Scheduler.next(learned, Grade.EASY, NOW);
        assertThat(easy.intervalDays()).isEqualTo(10); // 3 x 2.5 x 1.3, rounded
        assertThat(easy.ease()).isEqualTo(2.65);
        assertThat(Scheduler.next(Schedule.fresh(NOW), Grade.EASY, NOW).intervalDays()).isEqualTo(3);
    }

    @Test
    void easeNeverDropsBelowItsFloorAndIntervalsStopAtAYear() {
        var s = Schedule.fresh(NOW);
        for (int i = 0; i < 10; i++) s = Scheduler.next(s, Grade.AGAIN, NOW);
        assertThat(s.ease()).isEqualTo(1.3);

        var longOne = new Schedule(2.5, 300, 9, 0, NOW);
        assertThat(Scheduler.next(longOne, Grade.EASY, NOW).intervalDays()).isEqualTo(365);
    }
}
