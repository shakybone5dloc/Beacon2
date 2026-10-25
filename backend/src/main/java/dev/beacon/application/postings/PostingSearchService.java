package dev.beacon.application.postings;

import dev.beacon.application.apikeys.UserCredentials;
import dev.beacon.application.providers.Provider;
import dev.beacon.application.providers.ProviderCatalog;
import dev.beacon.contracts.postings.JobPostingResponse;
import dev.beacon.contracts.postings.JobSourceResponse;
import dev.beacon.contracts.postings.PostingSearchResponse;
import dev.beacon.contracts.postings.SourceResultResponse;
import dev.beacon.infrastructure.security.KeyCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Fans a search out to the selected sources in parallel and merges what comes back.
 * Each user sees the sources that need no key, plus the keyed ones they have
 * added a key for; sources switched off by configuration are hidden from everyone.
 */
@Service
public class PostingSearchService {

    private static final Logger log = LoggerFactory.getLogger(PostingSearchService.class);

    static final Duration SOURCE_TIMEOUT = Duration.ofSeconds(15);
    static final int MAX_RESULTS = 300;

    private final List<JobSource> sources;
    /** Source ids switched off by configuration, whatever keys are set (BEACON_DISABLED_SOURCES). */
    private final Set<String> disabled;
    private final ProviderCatalog catalog;
    private final UserCredentials credentials;
    private final Duration sourceTimeout;

    @Autowired
    public PostingSearchService(List<JobSource> sources,
                                @Value("${beacon.postings.disabled-sources:}") Set<String> disabled,
                                ProviderCatalog catalog, UserCredentials credentials) {
        this(sources, disabled, catalog, credentials, SOURCE_TIMEOUT);
    }

    PostingSearchService(List<JobSource> sources, Set<String> disabled, ProviderCatalog catalog,
                         UserCredentials credentials, Duration sourceTimeout) {
        this.sources = sources.stream().sorted(Comparator.comparing(JobSource::name)).toList();
        this.disabled = disabled.stream()
                .map(id -> id.strip().toLowerCase(Locale.ROOT))
                .filter(id -> !id.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.catalog = catalog;
        this.credentials = credentials;
        this.sourceTimeout = sourceTimeout;
    }

    /** The sources this user can search. */
    public List<JobSourceResponse> sources(UUID userId) {
        var saved = credentials.savedProviders(userId);
        return available(saved).stream()
                .map(s -> new JobSourceResponse(s.id(), s.name(), s.coverage(), s.attributionUrl(), saved.contains(s.id())))
                .toList();
    }

    /** Switched on, and either free of keys or keyed with one the user has saved. */
    private List<JobSource> available(Set<String> saved) {
        return sources.stream()
                .filter(s -> !disabled.contains(s.id()))
                .filter(s -> !needsKey(s) || saved.contains(s.id()))
                .toList();
    }

    private boolean needsKey(JobSource source) {
        return catalog.find(source.id()).map(Provider::needsKey).orElse(false);
    }

    /** Switched off for everyone by configuration (BEACON_DISABLED_SOURCES). */
    public boolean isTurnedOff(String id) {
        return disabled.contains(id);
    }

    public boolean isKnownSource(String id) {
        return sources.stream().anyMatch(s -> s.id().equals(id));
    }

    /** An empty selection means every source the user can search; ids they can't are ignored. */
    public PostingSearchResponse search(UUID userId, PostingQuery query, Set<String> sourceIds) {
        var saved = credentials.savedProviders(userId);
        var selected = available(saved).stream()
                .filter(s -> sourceIds.isEmpty() || sourceIds.contains(s.id()))
                .toList();

        var results = new ArrayList<SourceResultResponse>();
        var postings = new ArrayList<JobPosting>();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new LinkedHashMap<JobSource, Future<List<JobPosting>>>();
            for (var source : selected) {
                Map<String, String> keys;
                try {
                    keys = saved.contains(source.id())
                            ? credentials.credentials(userId, source.id()).orElse(Map.of())
                            : Map.of();
                } catch (KeyCipher.UnreadableKeyException ex) {
                    results.add(new SourceResultResponse(source.id(), 0, "Your saved " + source.name()
                            + " key can't be read any more. Add it again under Settings."));
                    continue;
                }
                futures.put(source, executor.submit(() -> source.search(query, keys)));
            }

            var deadline = Instant.now().plus(sourceTimeout);
            futures.forEach((source, future) -> {
                try {
                    var remaining = Math.max(0, Duration.between(Instant.now(), deadline).toMillis());
                    var found = future.get(remaining, TimeUnit.MILLISECONDS);
                    postings.addAll(found);
                    results.add(new SourceResultResponse(source.id(), found.size(), null));
                } catch (TimeoutException ex) {
                    future.cancel(true);
                    results.add(new SourceResultResponse(source.id(), 0, source.name() + " took too long to answer."));
                } catch (ExecutionException ex) {
                    log.warn("Job source {} failed", source.id(), ex.getCause());
                    results.add(new SourceResultResponse(source.id(), 0, failureMessage(source, ex.getCause(), saved)));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    results.add(new SourceResultResponse(source.id(), 0, "Search was interrupted."));
                }
            });
        }

        var merged = dedupe(postings).stream()
                .sorted(Comparator.comparing(JobPosting::postedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_RESULTS)
                .map(PostingSearchService::toResponse)
                .toList();
        return new PostingSearchResponse(merged, results);
    }

    /** A rejected key is the user's to fix, so it gets its own message. */
    private static String failureMessage(JobSource source, Throwable cause, Set<String> saved) {
        var rejected = cause instanceof HttpClientErrorException.Unauthorized
                || cause instanceof HttpClientErrorException.Forbidden;
        if (rejected && saved.contains(source.id())) {
            return source.name() + " rejected your API key. Check it under Settings.";
        }
        return "Could not reach " + source.name() + ".";
    }

    /** The same ad often appears on several boards; keep the first copy of each company + title. */
    private static List<JobPosting> dedupe(List<JobPosting> postings) {
        var byKey = new LinkedHashMap<String, JobPosting>();
        for (var posting : postings) {
            var key = (posting.company() + "|" + posting.title()).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            byKey.putIfAbsent(key, posting);
        }
        return List.copyOf(byKey.values());
    }

    private static JobPostingResponse toResponse(JobPosting p) {
        return new JobPostingResponse(p.source(), p.id(), p.title(), p.company(), p.location(), p.remote(),
                p.url(), p.postedAt(), p.closesOn(), p.salaryMin(), p.salaryMax(), p.salaryCurrency(),
                p.salaryPeriod(), p.salaryText(), p.tags(), p.snippet(), p.description());
    }
}
