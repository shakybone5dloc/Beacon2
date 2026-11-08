package dev.beacon.application.home;

import dev.beacon.contracts.applications.ApplicationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors {@code frontend/src/app/applications/attention.spec.ts}: the weekly email has to
 * flag the same applications, for the same reasons, as Home in the browser.
 */
class AttentionRulesTests {

    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-10-20");

    @Test
    void closedApplicationsAreNeverFlagged() {
        assertThat(flag(app("REJECTED").followUpOn(LocalDate.parse("2026-10-01")))).isEmpty();
    }

    @Test
    void flagsAnApplicationOnceItsStatusLimitIsReached() {
        assertThat(flag(app("APPLIED").changed(daysAgo(6.9)))).isEmpty();
        assertThat(reason(app("APPLIED").changed(daysAgo(7)))).isEqualTo("No movement in 7 days");
        assertThat(reason(app("OFFER").changed(daysAgo(4)))).isEqualTo("No movement in 4 days");
        assertThat(flag(app("SAVED").changed(daysAgo(4)))).isEmpty();
        assertThat(flag(app("INTERVIEWING").changed(daysAgo(60)))).isEmpty();
    }

    @Test
    void interviewsHoldOffTheAgeRuleAndQuietAfterOneIsFlagged() {
        assertThat(flag(app("APPLIED").changed(daysAgo(30)).nextInterview(NOW.plus(Duration.ofDays(4))))).isEmpty();
        assertThat(reason(app("INTERVIEWING").changed(daysAgo(40)).lastInterview(daysAgo(9))))
                .isEqualTo("No news 9 days after the interview");
        assertThat(flag(app("INTERVIEWING").changed(daysAgo(40)).lastInterview(daysAgo(6)))).isEmpty();
        assertThat(flag(app("INTERVIEWING").lastInterview(daysAgo(20)).followUpOn(LocalDate.parse("2026-10-25")))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "2026-10-30, ",
            "2026-10-23, Closes in 3d",
            "2026-10-20, Closes today",
            "2026-10-18, Closed 2d ago",
    })
    void flagsASavedApplicationAsItsDeadlineNears(String applyBy, String reason) {
        var flagged = flag(app("SAVED").applyBy(LocalDate.parse(applyBy)));
        assertThat(flagged.map(AttentionRules.Flag::reason).orElse(null)).isEqualTo(reason);
    }

    @Test
    void aDeadlineCountsOnlyWhileSaved() {
        assertThat(flag(app("SAVED").changed(daysAgo(20)).applyBy(LocalDate.parse("2026-11-30")))).isEmpty();
        // Moved past Saved, the deadline is ignored and the age rule applies again.
        assertThat(reason(app("APPLIED").changed(daysAgo(8)).applyBy(LocalDate.parse("2026-10-18"))))
                .isEqualTo("No movement in 8 days");
    }

    @ParameterizedTest
    @CsvSource({
            "2026-10-25, ",
            "2026-10-20, Follow up today",
            "2026-10-17, Follow-up 3d overdue",
    })
    void flagsAFollowUpOnceDue(String followUpOn, String reason) {
        var flagged = flag(app("INTERVIEWING").followUpOn(LocalDate.parse(followUpOn)));
        assertThat(flagged.map(AttentionRules.Flag::reason).orElse(null)).isEqualTo(reason);
        flagged.ifPresent(f -> assertThat(f.followUpDue()).isTrue());
    }

    @Test
    void aFollowUpWinsOverTheDeadlineAndHoldsOffTheAgeRule() {
        assertThat(reason(app("SAVED").applyBy(LocalDate.parse("2026-10-21")).followUpOn(TODAY))).isEqualTo("Follow up today");
        assertThat(flag(app("APPLIED").changed(daysAgo(30)).followUpOn(LocalDate.parse("2026-10-27")))).isEmpty();
    }

    @Test
    void listsDatedReasonsFirstByTheirDate() {
        var list = AttentionRules.needingAttention(List.of(
                app("APPLIED").role("quiet").changed(daysAgo(8)).build(),
                app("INTERVIEWING").role("overdue").followUpOn(LocalDate.parse("2026-10-18")).build(),
                app("SAVED").role("closing").applyBy(LocalDate.parse("2026-10-21")).build(),
                app("REJECTED").role("closed").build()), NOW, TODAY);

        assertThat(list).extracting(f -> f.application().role()).containsExactly("overdue", "closing", "quiet");
    }

    @Test
    void wordsDurationsAsTheBrowserDoes() {
        assertThat(AttentionRules.durationBetween(NOW, NOW.plusSeconds(1800))).isEqualTo("less than an hour");
        assertThat(AttentionRules.durationBetween(NOW, NOW.plusSeconds(3600))).isEqualTo("1 hour");
        assertThat(AttentionRules.durationBetween(NOW, NOW.plus(Duration.ofDays(1)))).isEqualTo("1 day");
    }

    private static Optional<AttentionRules.Flag> flag(Builder app) {
        return AttentionRules.flag(app.build(), NOW, TODAY);
    }

    private static String reason(Builder app) {
        return flag(app).map(AttentionRules.Flag::reason).orElse(null);
    }

    private static Instant daysAgo(double days) {
        return NOW.minus(Duration.ofMillis((long) (days * 86_400_000)));
    }

    private static Builder app(String status) {
        return new Builder(status);
    }

    /** Only the fields the rules read; the rest are fixed. */
    private static final class Builder {
        private final String status;
        private String role = "Engineer";
        private Instant changed = NOW;
        private LocalDate applyBy;
        private LocalDate followUpOn;
        private Instant nextInterview;
        private Instant lastInterview;

        Builder(String status) { this.status = status; }
        Builder role(String v) { role = v; return this; }
        Builder changed(Instant v) { changed = v; return this; }
        Builder applyBy(LocalDate v) { applyBy = v; return this; }
        Builder followUpOn(LocalDate v) { followUpOn = v; return this; }
        Builder nextInterview(Instant v) { nextInterview = v; return this; }
        Builder lastInterview(Instant v) { lastInterview = v; return this; }

        ApplicationResponse build() {
            return new ApplicationResponse(UUID.randomUUID(), null, "Acme", role, null, null, null, null, applyBy,
                    followUpOn, null, null, null, null, null, null, null, null, null, null, null, status, NOW, NOW,
                    changed, nextInterview, lastInterview, null, 0L, List.of());
        }
    }
}
