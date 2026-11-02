package dev.beacon.application.searches;

import dev.beacon.application.applications.ApplicationService;
import dev.beacon.application.fit.FitService;
import dev.beacon.application.postings.PostingSearchService;
import dev.beacon.contracts.applications.ApplicationResponse;
import dev.beacon.contracts.applications.CreateApplicationRequest;
import dev.beacon.contracts.searches.JobMatchResponse;
import dev.beacon.contracts.searches.MatchSummaryResponse;
import dev.beacon.domain.searches.JobMatch;
import dev.beacon.infrastructure.persistence.JobMatchRepository;
import dev.beacon.infrastructure.persistence.SavedSearchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** What the user does with the postings their saved searches found. */
@Service
public class MatchService {

    private static final Pattern CURRENCY = Pattern.compile("[A-Za-z]{3}");
    private static final Set<String> PERIODS = Set.of("year", "month", "week", "day", "hour");

    private final JobMatchRepository matches;
    private final SavedSearchRepository searches;
    private final ApplicationService applications;
    private final FitService fit;
    private final PostingSearchService postings;
    private final Clock clock;

    public MatchService(JobMatchRepository matches, SavedSearchRepository searches, ApplicationService applications,
                        FitService fit, PostingSearchService postings, Clock clock) {
        this.matches = matches;
        this.searches = searches;
        this.applications = applications;
        this.fit = fit;
        this.postings = postings;
        this.clock = clock;
    }

    /** Best score first, then newest posting; unscored matches after the scored ones. */
    @Transactional(readOnly = true)
    public List<JobMatchResponse> list(UUID ownerId, JobMatch.Status status) {
        var names = new HashMap<UUID, String>();
        searches.findAllByOwnerIdOrderByCreatedAtAsc(ownerId).forEach(s -> names.put(s.getId(), s.getName()));
        return matches.findAllByOwnerIdAndStatusOrderByFoundAtDesc(ownerId, status).stream()
                .sorted(Comparator
                        .comparing(JobMatch::getScore, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(JobMatch::getPostedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(m -> toResponse(m, names.get(m.getSavedSearchId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public MatchSummaryResponse summary(UUID ownerId) {
        return new MatchSummaryResponse(matches.countByOwnerIdAndStatus(ownerId, JobMatch.Status.NEW));
    }

    /**
     * Saves the match to the board as a SAVED application with the posting's details. Its
     * close date becomes the apply-by date, and its fit score, if any, becomes the
     * application's fit at no cost. Saving again returns the same application.
     */
    @Transactional
    public Optional<ApplicationResponse> save(UUID ownerId, UUID matchId) {
        var found = matches.findByIdAndOwnerId(matchId, ownerId);
        if (found.isEmpty()) return Optional.empty();
        var match = found.get();
        if (match.getApplicationId() != null) {
            var existing = applications.getById(ownerId, match.getApplicationId());
            if (existing.isPresent()) return existing;
        }

        var today = LocalDate.now(clock);
        var applyBy = match.getClosesOn() != null && !match.getClosesOn().isBefore(today) ? match.getClosesOn() : null;
        var sourceName = postings.sources(ownerId).stream()
                .filter(s -> s.id().equals(match.getSource())).map(s -> s.name()).findFirst().orElse(match.getSource());
        var app = applications.create(ownerId, new CreateApplicationRequest(
                null,
                match.getCompany(),
                cut(match.getTitle(), 200),
                null,
                match.getUrl().length() <= 2048 ? match.getUrl() : null,
                null,
                applyBy,
                null,
                cut(match.getLocation(), 500),
                match.isRemote(),
                positive(match.getSalaryMin()),
                positive(match.getSalaryMax()),
                match.getSalaryCurrency() != null && CURRENCY.matcher(match.getSalaryCurrency()).matches()
                        ? match.getSalaryCurrency().toUpperCase() : null,
                match.getSalaryPeriod() != null && PERIODS.contains(match.getSalaryPeriod().toLowerCase())
                        ? match.getSalaryPeriod().toLowerCase() : null,
                cut(match.getSalaryText(), 100),
                cut(sourceName, 100),
                match.getPostedAt() == null ? null : match.getPostedAt().atZone(ZoneOffset.UTC).toLocalDate(),
                cut(match.getDescription() != null ? match.getDescription() : match.getSnippet(), 100_000)));
        if (match.getFitScoreId() != null) fit.link(ownerId, app.id(), match.getFitScoreId());
        match.savedAs(app.id(), Instant.now(clock));
        return applications.getById(ownerId, app.id());
    }

    @Transactional
    public Optional<JobMatchResponse> dismiss(UUID ownerId, UUID matchId) {
        return matches.findByIdAndOwnerId(matchId, ownerId).map(match -> {
            if (match.getStatus() == JobMatch.Status.NEW) match.dismiss(Instant.now(clock));
            return toResponse(match, searchName(match));
        });
    }

    /** Undoes a dismissal. */
    @Transactional
    public Optional<JobMatchResponse> restore(UUID ownerId, UUID matchId) {
        return matches.findByIdAndOwnerId(matchId, ownerId).map(match -> {
            if (match.getStatus() == JobMatch.Status.DISMISSED) match.restore();
            return toResponse(match, searchName(match));
        });
    }

    private String searchName(JobMatch match) {
        return match.getSavedSearchId() == null ? null
                : searches.findById(match.getSavedSearchId()).map(s -> s.getName()).orElse(null);
    }

    private static JobMatchResponse toResponse(JobMatch m, String searchName) {
        return new JobMatchResponse(m.getId(), m.getStatus().name(), m.getSavedSearchId(), searchName, m.getSource(),
                m.getTitle(), m.getCompany(), m.getLocation(), m.isRemote(), m.getUrl(), m.getPostedAt(),
                m.getClosesOn(), m.getSalaryMin(), m.getSalaryMax(), m.getSalaryCurrency(), m.getSalaryPeriod(),
                m.getSalaryText(), m.getSnippet(), m.getScore(), m.getApplicationId(), m.getFoundAt());
    }

    private static Integer positive(Integer value) {
        return value != null && value >= 0 ? value : null;
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
