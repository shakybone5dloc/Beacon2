package dev.beacon.application.searches;

import dev.beacon.application.fit.FitScoreException;
import dev.beacon.application.fit.FitService;
import dev.beacon.application.postings.PostingQuery;
import dev.beacon.application.postings.PostingSearchService;
import dev.beacon.application.usage.AiUsageService;
import dev.beacon.contracts.fit.ScorePostingFitRequest;
import dev.beacon.contracts.postings.JobPostingResponse;
import dev.beacon.domain.searches.JobMatch;
import dev.beacon.domain.searches.SavedSearch;
import dev.beacon.domain.searches.SearchRun;
import dev.beacon.infrastructure.persistence.JobApplicationRepository;
import dev.beacon.infrastructure.persistence.JobMatchRepository;
import dev.beacon.infrastructure.persistence.SavedSearchRepository;
import dev.beacon.infrastructure.persistence.SearchRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Runs a saved search: gather from its sources, filter without AI, drop what the user
 * has already seen or saved, keep the rest as matches, then fit-score the newest few
 * if the search names a resume and the user is within their AI budget.
 */
@Service
public class SavedSearchRunner {

    private static final Logger log = LoggerFactory.getLogger(SavedSearchRunner.class);

    /** Fit checks per run; about $0.15 on gpt-6-sol. Unscored matches get their turn next run. */
    static final int SCORES_PER_RUN = 20;

    private final SavedSearchRepository searches;
    private final SearchRunRepository runs;
    private final JobMatchRepository matches;
    private final JobApplicationRepository applications;
    private final PostingSearchService postings;
    private final FitService fit;
    private final AiUsageService usage;
    private final TransactionTemplate tx;
    private final Clock clock;
    /** Run now returns at once and the run carries on here; tests run it inline instead. */
    private final ExecutorService background;

    public SavedSearchRunner(SavedSearchRepository searches, SearchRunRepository runs, JobMatchRepository matches,
                             JobApplicationRepository applications, PostingSearchService postings, FitService fit,
                             AiUsageService usage, PlatformTransactionManager transactions, Clock clock,
                             @Value("${beacon.saved-searches.run-in-background:true}") boolean runInBackground) {
        this.searches = searches;
        this.runs = runs;
        this.matches = matches;
        this.applications = applications;
        this.postings = postings;
        this.fit = fit;
        this.usage = usage;
        this.tx = new TransactionTemplate(transactions);
        this.clock = clock;
        this.background = runInBackground ? Executors.newVirtualThreadPerTaskExecutor() : null;
    }

    /** Runs every search that's due, one after another. Called by the scheduler. */
    public void runDue() {
        var now = Instant.now(clock);
        for (var id : searches.findDue(now, now.minus(SavedSearch.STALE_RUN))) {
            if (claim(id, null).isPresent()) run(id);
        }
    }

    /**
     * Starts a run now, unless one is already under way. Empty when the search isn't the
     * owner's; false when it's already running.
     */
    public Optional<Boolean> runNow(UUID ownerId, UUID searchId) {
        if (searches.findByIdAndOwnerId(searchId, ownerId).isEmpty()) return Optional.empty();
        if (claim(searchId, ownerId).isEmpty()) return Optional.of(false);
        if (background != null) {
            background.submit(() -> run(searchId));
        } else {
            run(searchId);
        }
        return Optional.of(true);
    }

    /** Marks the search as running and books its next run, in one short transaction. */
    private Optional<SavedSearch> claim(UUID searchId, UUID ownerId) {
        return tx.execute(status -> {
            var now = Instant.now(clock);
            var found = ownerId == null ? searches.findById(searchId) : searches.findByIdAndOwnerId(searchId, ownerId);
            return found.filter(search -> !search.isRunning(now)).map(search -> {
                search.startRun(now);
                return search;
            });
        });
    }

    private void run(UUID searchId) {
        var search = searches.findById(searchId).orElseThrow();
        var run = SearchRun.start(searchId, Instant.now(clock));
        try {
            var fresh = gather(search, run);
            saveMatches(search, fresh, run);
            score(search, run);
        } catch (RuntimeException ex) {
            log.warn("Saved search {} failed", searchId, ex);
            run.note("The run stopped early: something went wrong on Beacon's side.");
        } finally {
            run.finish(Instant.now(clock));
            runs.save(run);
            tx.executeWithoutResult(status -> searches.findById(searchId).ifPresent(SavedSearch::finishRun));
        }
    }

    /** Searches every chosen source, then keeps the postings that pass the filters and are new to the user. */
    private List<JobPostingResponse> gather(SavedSearch search, SearchRun run) {
        var owner = search.getOwnerId();
        var query = new PostingQuery(search.getKeywords(), search.getLocation(), search.isRemoteOnly());
        var result = postings.search(owner, query, Set.copyOf(search.getSources()));
        var names = postings.sources(owner).stream().collect(Collectors.toMap(s -> s.id(), s -> s.name()));
        for (var source : result.sources()) {
            run.note(source.error() != null ? source.error()
                    : names.getOrDefault(source.id(), source.id()) + ": " + source.count());
        }
        run.gathered(result.postings().size());

        var now = Instant.now(clock);
        var kept = new LinkedHashMap<String, JobPostingResponse>();
        for (var posting : result.postings()) {
            if (SearchFilters.keep(posting, search.getExcludeWords(), search.getSalaryMin(), search.getPostedWithinDays(), now)) {
                kept.putIfAbsent(MatchKeys.of(posting.company(), posting.title()), posting);
            }
        }
        if (kept.isEmpty()) return List.of();

        var seen = new HashSet<>(matches.findKnownKeys(owner, kept.keySet()));
        var onBoardUrls = new HashSet<String>();
        for (var app : applications.findAllByOwnerIdOrderByCreatedAtDesc(owner)) {
            seen.add(MatchKeys.of(app.getCompanyName(), app.getRole()));
            if (app.getJobUrl() != null) onBoardUrls.add(app.getJobUrl());
        }
        return kept.entrySet().stream()
                .filter(e -> !seen.contains(e.getKey()) && !onBoardUrls.contains(e.getValue().url()))
                .map(Map.Entry::getValue)
                .toList();
    }

    private void saveMatches(SavedSearch search, List<JobPostingResponse> fresh, SearchRun run) {
        var now = Instant.now(clock);
        var saved = fresh.stream()
                .map(p -> JobMatch.found(search.getOwnerId(), search.getId(), MatchKeys.of(p.company(), p.title()),
                        new JobMatch.Posting(p.source(), p.id(), p.title(), p.company(), p.location(), p.remote(),
                                p.url(), p.postedAt(), p.closesOn(), p.salaryMin(), p.salaryMax(), p.salaryCurrency(),
                                p.salaryPeriod(), p.salaryText(), p.snippet(), p.description()), now))
                .toList();
        matches.saveAll(saved);
        run.foundNew(saved.size());
    }

    /**
     * Scores up to {@link #SCORES_PER_RUN} of the search's unscored matches, newest first,
     * including ones an earlier run didn't get to. Stops at the budget, or at a problem the
     * user has to fix (no key, unreadable resume).
     */
    private void score(SavedSearch search, SearchRun run) {
        if (search.getResumeDocumentId() == null) return;
        var owner = search.getOwnerId();
        var pending = matches.findUnscored(owner, search.getId(), Limit.of(SCORES_PER_RUN));
        for (var match : pending) {
            if (usage.isOverBudget(owner, search.getTimeZone())) {
                run.note("Fit checks paused: you've reached your monthly AI budget.");
                return;
            }
            try {
                var result = fit.scorePosting(owner, new ScorePostingFitRequest(search.getResumeDocumentId(),
                        cut(match.getTitle(), 200), cut(match.getCompany(), 200), postingText(match)));
                match.scored(result.id(), result.score(), search.getMinScore());
                matches.save(match);
                run.scoredOne();
            } catch (FitScoreException ex) {
                run.note("Fit checks stopped: " + ex.getMessage());
                return;
            }
        }
    }

    /** The full description when the source gave one, else its snippet, else just the title. */
    private static String postingText(JobMatch match) {
        var text = match.getDescription() != null && !match.getDescription().isBlank() ? match.getDescription()
                : match.getSnippet() != null && !match.getSnippet().isBlank() ? match.getSnippet()
                : match.getTitle();
        return cut(text, 60_000);
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
