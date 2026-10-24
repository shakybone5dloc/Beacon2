package dev.beacon.application.postings;

import dev.beacon.contracts.postings.JobPostingResponse;
import dev.beacon.contracts.postings.JobSourceResponse;
import dev.beacon.contracts.postings.PostingSearchResponse;
import dev.beacon.contracts.postings.SourceResultResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Fans a search out to the selected sources in parallel and merges what comes back. */
@Service
public class PostingSearchService {

    private static final Logger log = LoggerFactory.getLogger(PostingSearchService.class);

    static final Duration SOURCE_TIMEOUT = Duration.ofSeconds(15);
    static final int MAX_RESULTS = 300;

    private final List<JobSource> sources;
    private final Duration sourceTimeout;

    @Autowired
    public PostingSearchService(List<JobSource> sources) {
        this(sources, SOURCE_TIMEOUT);
    }

    PostingSearchService(List<JobSource> sources, Duration sourceTimeout) {
        this.sources = sources.stream().sorted(Comparator.comparing(JobSource::name)).toList();
        this.sourceTimeout = sourceTimeout;
    }

    public List<JobSourceResponse> sources() {
        return sources.stream()
                .map(s -> new JobSourceResponse(s.id(), s.name(), s.coverage(), s.attributionUrl(),
                        s.isConfigured(), s.isConfigured() ? null : s.setupHint()))
                .toList();
    }

    public boolean isKnownSource(String id) {
        return sources.stream().anyMatch(s -> s.id().equals(id));
    }

    /** An empty selection means every configured source; unknown ids are ignored. */
    public PostingSearchResponse search(PostingQuery query, Set<String> sourceIds) {
        var selected = select(sourceIds);

        var results = new ArrayList<SourceResultResponse>();
        var postings = new ArrayList<JobPosting>();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new LinkedHashMap<JobSource, Future<List<JobPosting>>>();
            for (var source : selected) {
                if (source.isConfigured()) {
                    futures.put(source, executor.submit(() -> source.search(query)));
                } else {
                    results.add(new SourceResultResponse(source.id(), 0, "Needs an API key. " + source.setupHint()));
                }
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
                    results.add(new SourceResultResponse(source.id(), 0, "Could not reach " + source.name() + "."));
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

    private List<JobSource> select(Set<String> sourceIds) {
        if (sourceIds.isEmpty()) {
            return sources.stream().filter(JobSource::isConfigured).toList();
        }
        return sources.stream().filter(s -> sourceIds.contains(s.id())).toList();
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
