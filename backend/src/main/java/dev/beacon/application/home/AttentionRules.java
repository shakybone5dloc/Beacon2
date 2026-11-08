package dev.beacon.application.home;

import dev.beacon.contracts.applications.ApplicationResponse;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Whether an application needs the user, and why: the server's copy of the browser's rules in
 * {@code frontend/src/app/applications/attention.ts}, so the weekly email says what Home says.
 * Change both together; {@code AttentionRulesTests} mirrors {@code attention.spec.ts}.
 */
public final class AttentionRules {

    /**
     * Days in a status before an application counts as gone quiet, used only when no apply-by
     * or follow-up date says otherwise. Null never flags; closed statuses are absent.
     */
    static final Map<String, Integer> STALE_AFTER_DAYS = new HashMap<>();
    static {
        STALE_AFTER_DAYS.put("SAVED", 7);
        STALE_AFTER_DAYS.put("APPLIED", 7);
        STALE_AFTER_DAYS.put("INTERVIEWING", null);
        STALE_AFTER_DAYS.put("OFFER", 3);
    }

    /** How close an apply-by date gets before a Saved application is flagged. */
    static final int APPLY_BY_WARNING_DAYS = 3;

    /** Days after the last interview, with none scheduled, before an Interviewing application is flagged. */
    static final int QUIET_AFTER_INTERVIEW_DAYS = 7;

    /**
     * @param reason       e.g. "Follow-up 2d overdue" or "No movement in 8 days"
     * @param urgentOn     the follow-up or apply-by date that made it urgent; null for one gone quiet
     * @param followUpDue  true when marking the follow-up done would clear it
     */
    public record Flag(ApplicationResponse application, String reason, LocalDate urgentOn, boolean followUpDue) {}

    private AttentionRules() {}

    /**
     * Applications that need attention, most urgent first: dated reasons by their date, then
     * the ones that have gone quiet.
     *
     * @param today the user's local date, for counting days to a follow-up or deadline
     */
    public static List<Flag> needingAttention(List<ApplicationResponse> applications, Instant now, LocalDate today) {
        return applications.stream()
                .map(app -> flag(app, now, today))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(Flag::urgentOn, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** Empty when the application is closed or nothing is due. */
    public static Optional<Flag> flag(ApplicationResponse app, Instant now, LocalDate today) {
        if (!STALE_AFTER_DAYS.containsKey(app.status())) return Optional.empty();

        var deadline = "SAVED".equals(app.status()) ? app.applyBy() : null;
        var followUp = app.followUpOn();
        var followUpUrgent = followUp != null && daysUntil(followUp, today) <= 0;
        var deadlineUrgent = deadline != null && daysUntil(deadline, today) <= APPLY_BY_WARNING_DAYS;

        if (followUpUrgent) {
            return Optional.of(new Flag(app, followUpLabel(followUp, today), followUp, true));
        }
        if (deadlineUrgent) {
            return Optional.of(new Flag(app, deadlineLabel(deadline, today), deadline, false));
        }
        // A date ahead, or an interview scheduled, means "leave it alone until then".
        if (deadline != null || followUp != null || app.nextInterviewAt() != null) return Optional.empty();

        var lastInterviewAt = "INTERVIEWING".equals(app.status()) ? app.lastInterviewAt() : null;
        var limit = lastInterviewAt != null ? Integer.valueOf(QUIET_AFTER_INTERVIEW_DAYS) : STALE_AFTER_DAYS.get(app.status());
        var since = lastInterviewAt != null ? lastInterviewAt : app.statusChangedAt();
        if (limit == null || Duration.between(since, now).compareTo(Duration.ofDays(limit)) < 0) return Optional.empty();

        var reason = lastInterviewAt != null
                ? "No news " + durationBetween(since, now) + " after the interview"
                : "No movement in " + durationBetween(since, now);
        return Optional.of(new Flag(app, reason, null, false));
    }

    /** "Follow-up 2d overdue", "Follow up today" or "Follow up in 3d". */
    static String followUpLabel(LocalDate followUpOn, LocalDate today) {
        var days = daysUntil(followUpOn, today);
        return days < 0 ? "Follow-up " + -days + "d overdue" : days == 0 ? "Follow up today" : "Follow up in " + days + "d";
    }

    /** "Closed 2d ago", "Closes today" or "Closes in 3d". */
    static String deadlineLabel(LocalDate applyBy, LocalDate today) {
        var days = daysUntil(applyBy, today);
        return days < 0 ? "Closed " + -days + "d ago" : days == 0 ? "Closes today" : "Closes in " + days + "d";
    }

    /** "less than an hour", "5 hours" or "3 days", as the browser says it. */
    static String durationBetween(Instant from, Instant to) {
        var gap = Duration.between(from, to);
        if (gap.compareTo(Duration.ofHours(1)) < 0) return "less than an hour";
        if (gap.compareTo(Duration.ofDays(1)) < 0) return plural(gap.toHours(), "hour");
        return plural(gap.toDays(), "day");
    }

    private static long daysUntil(LocalDate date, LocalDate today) {
        return ChronoUnit.DAYS.between(today, date);
    }

    private static String plural(long n, String unit) {
        return n + " " + unit + (n == 1 ? "" : "s");
    }
}
