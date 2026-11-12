package dev.beacon.domain.study;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

/**
 * When to see a card next, after the user grades how well they knew it: a variant of SM-2,
 * the algorithm behind most spaced-repetition apps. Each card has an ease (how fast its gaps
 * grow) and an interval in days; knowing it stretches the interval, missing it starts over.
 * Pure: the same schedule, grade and time always give the same answer.
 */
public final class Scheduler {

    public static final double STARTING_EASE = 2.5;
    static final double MIN_EASE = 1.3;
    static final int MAX_INTERVAL_DAYS = 365;
    /** A missed card comes back in the same sitting. */
    static final Duration RELEARN = Duration.ofMinutes(10);

    private Scheduler() {}

    /**
     * @param ease         how fast the interval grows; 2.5 for a new card, never below 1.3
     * @param intervalDays days until the next review; 0 for a new card or one just missed
     * @param repetitions  times in a row it's been known
     * @param lapses       times it's been missed after being learned
     */
    public record Schedule(double ease, int intervalDays, int repetitions, int lapses, Instant dueAt) {

        /** A card nobody has studied yet, due straight away. */
        public static Schedule fresh(Instant now) {
            return new Schedule(STARTING_EASE, 0, 0, 0, now);
        }
    }

    public static Schedule next(Schedule current, Grade grade, Instant now) {
        var ease = current.ease();
        var reps = current.repetitions();
        var interval = current.intervalDays();

        if (grade == Grade.AGAIN) {
            return new Schedule(round(Math.max(MIN_EASE, ease - 0.2)), 0, 0,
                    current.lapses() + (reps > 0 ? 1 : 0), now.plus(RELEARN));
        }

        int next = switch (grade) {
            // Known, but with effort: a little longer, and the card gets a bit harder.
            case HARD -> interval == 0 ? 1 : Math.max(interval + 1, (int) Math.round(interval * 1.2));
            case GOOD -> reps == 0 ? 1 : reps == 1 ? 3 : Math.max(interval + 1, (int) Math.round(interval * ease));
            case EASY -> reps == 0 ? 3 : Math.max(interval + 1, (int) Math.round(interval * ease * 1.3));
            case AGAIN -> throw new IllegalStateException("handled above");
        };
        var nextEase = switch (grade) {
            case HARD -> Math.max(MIN_EASE, ease - 0.15);
            case EASY -> ease + 0.15;
            default -> ease;
        };
        next = Math.min(next, MAX_INTERVAL_DAYS);
        return new Schedule(round(nextEase), next, reps + 1, current.lapses(), now.plus(Duration.ofDays(next)));
    }

    /**
     * The last stretch before an interview, when every kept card should be seen once more: the
     * day before it.
     */
    public static final Duration FINAL_LOOK = Duration.ofDays(1);
    /** The latest a card is brought back before the interview itself. */
    static final Duration LAST_CALL = Duration.ofHours(2);
    /** A card isn't brought forward to sooner than this after it was just answered. */
    static final Duration MIN_GAP = Duration.ofHours(3);

    /**
     * Like {@link #next(Schedule, Grade, Instant)}, but aimed at an interview: a card that would
     * next be due after it comes back the day before instead, or about two hours before once
     * that's passed. The interval still grows as usual, so after the interview the card returns
     * to its normal pace. With no interview ahead, or one too close to aim at, it's plain SM-2.
     */
    public static Schedule next(Schedule current, Grade grade, Instant now, Instant interviewAt) {
        var plain = next(current, grade, now);
        if (interviewAt == null || !interviewAt.isAfter(now)) return plain;
        var target = Stream.of(interviewAt.minus(FINAL_LOOK), interviewAt.minus(LAST_CALL))
                .filter(at -> !at.isBefore(now.plus(MIN_GAP)))
                .findFirst();
        if (target.isEmpty() || !plain.dueAt().isAfter(target.get())) return plain;
        return new Schedule(plain.ease(), plain.intervalDays(), plain.repetitions(), plain.lapses(), target.get());
    }

    private static double round(double ease) {
        return Math.round(ease * 100) / 100.0;
    }
}
