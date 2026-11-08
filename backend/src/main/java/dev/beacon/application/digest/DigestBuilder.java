package dev.beacon.application.digest;

import dev.beacon.application.applications.ApplicationService;
import dev.beacon.application.home.AttentionRules;
import dev.beacon.application.home.HomeService;
import dev.beacon.application.searches.MatchService;
import dev.beacon.application.stats.StatsService;
import dev.beacon.application.study.StudyService;
import dev.beacon.contracts.applications.ApplicationResponse;
import dev.beacon.contracts.searches.JobMatchResponse;
import dev.beacon.domain.applications.JobApplication;
import dev.beacon.domain.searches.JobMatch;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Works out the digest for one user: the same sections and rules as Home in the browser,
 * from the same services the pages use.
 */
@Component
public class DigestBuilder {

    /** How far ahead Coming up looks, as on Home. */
    static final Duration UPCOMING = Duration.ofDays(14);

    /** How many new matches are listed, as on Home. */
    static final int QUEUE_SIZE = 5;

    private static final Map<String, String> STAGES = Map.of(
            "SAVED", "Saved", "APPLIED", "Applied", "INTERVIEWING", "Interviewing", "OFFER", "Offer");
    private static final List<String> STAGE_ORDER = List.of("SAVED", "APPLIED", "INTERVIEWING", "OFFER");

    private final ApplicationService applications;
    private final MatchService matches;
    private final StudyService study;
    private final StatsService stats;
    private final HomeService home;

    public DigestBuilder(ApplicationService applications, MatchService matches, StudyService study,
                         StatsService stats, HomeService home) {
        this.applications = applications;
        this.matches = matches;
        this.study = study;
        this.stats = stats;
        this.home = home;
    }

    public Digest build(UUID userId, String displayName, ZoneId zone, Instant now) {
        var today = now.atZone(zone).toLocalDate();
        var apps = applications.list(userId);
        var decks = study.decks(userId);

        var attention = AttentionRules.needingAttention(apps, now, today).stream()
                .map(f -> new Digest.Flagged(f.application().id(), employer(f.application()), f.application().role(), f.reason()))
                .toList();

        var until = now.plus(UPCOMING);
        var upcoming = apps.stream()
                .filter(a -> a.nextInterviewAt() != null && !a.nextInterviewAt().isBefore(now) && !a.nextInterviewAt().isAfter(until))
                .sorted(Comparator.comparing(ApplicationResponse::nextInterviewAt))
                .map(a -> new Digest.Interview(a.id(), employer(a), a.role(), a.nextInterviewAt().atZone(zone),
                        decks.stream().filter(d -> a.id().equals(d.applicationId())).mapToLong(d -> d.due()).sum()))
                .toList();

        var newMatches = matches.list(userId, JobMatch.Status.NEW);
        var queue = newMatches.stream()
                .sorted(Comparator.comparing((JobMatchResponse m) -> m.score() == null ? -1 : m.score()).reversed()
                        .thenComparing(JobMatchResponse::foundAt, Comparator.reverseOrder()))
                .limit(QUEUE_SIZE)
                .map(m -> new Digest.Match(m.title(), m.company(), m.score(), m.url()))
                .toList();

        var dueDecks = decks.stream()
                .filter(d -> d.due() > 0)
                .sorted(Comparator.comparingLong(d -> -d.due()))
                .map(d -> new Digest.Deck(d.id(), d.name(), d.due()))
                .toList();

        var numbers = stats.stats(userId, null, zone);
        var weeks = numbers.weeks();
        var week = new Digest.Week(
                weeks.isEmpty() ? 0 : weeks.getLast().applied(),
                weeks.size() < 2 ? 0 : weeks.get(weeks.size() - 2).applied(),
                numbers.overall().applied(), numbers.overall().responded(),
                STAGE_ORDER.stream()
                        .map(s -> new Digest.Stage(STAGES.get(s), apps.stream().filter(a -> s.equals(a.status())).count()))
                        .toList());

        var todos = home.todos(userId);
        return new Digest(firstName(displayName), today, attention, upcoming, queue, newMatches.size(), dueDecks, week,
                todos.missingSentResume(), todos.openSuggestions());
    }

    /** As the browser says it: the company, or the agency for an undisclosed client. */
    static String employer(ApplicationResponse app) {
        if (app.company() != null) return app.company();
        return app.viaAgency() != null
                ? JobApplication.UNDISCLOSED_EMPLOYER + " · via " + app.viaAgency()
                : JobApplication.UNDISCLOSED_EMPLOYER;
    }

    private static String firstName(String displayName) {
        if (displayName == null || displayName.isBlank()) return null;
        return Objects.requireNonNull(displayName.strip().split("\\s+")[0]);
    }
}
