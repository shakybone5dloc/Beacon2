package dev.beacon.application.searches;

import dev.beacon.application.documents.DocumentService;
import dev.beacon.application.postings.PostingSearchService;
import dev.beacon.contracts.searches.SaveSearchRequest;
import dev.beacon.contracts.searches.SavedSearchResponse;
import dev.beacon.contracts.searches.SearchRunResponse;
import dev.beacon.domain.searches.SavedSearch;
import dev.beacon.infrastructure.persistence.JobMatchRepository;
import dev.beacon.infrastructure.persistence.SavedSearchRepository;
import dev.beacon.infrastructure.persistence.SearchRunRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Each user's saved searches. Running them is {@link SavedSearchRunner}'s job. */
@Service
public class SavedSearchService {

    static final int DEFAULT_POSTED_WITHIN_DAYS = 7;

    private final SavedSearchRepository searches;
    private final SearchRunRepository runs;
    private final JobMatchRepository matches;
    private final PostingSearchService postings;
    private final DocumentService documents;
    private final Clock clock;

    public SavedSearchService(SavedSearchRepository searches, SearchRunRepository runs, JobMatchRepository matches,
                              PostingSearchService postings, DocumentService documents, Clock clock) {
        this.searches = searches;
        this.runs = runs;
        this.matches = matches;
        this.postings = postings;
        this.documents = documents;
        this.clock = clock;
    }

    /** A setting that can't be used, named by its field so the form can show it there. */
    public static class InvalidSearchException extends RuntimeException {
        private final String field;

        public InvalidSearchException(String field, String message) {
            super(message);
            this.field = field;
        }

        public String getField() { return field; }
    }

    @Transactional(readOnly = true)
    public List<SavedSearchResponse> list(UUID ownerId) {
        var counts = newMatchCounts(ownerId);
        return searches.findAllByOwnerIdOrderByCreatedAtAsc(ownerId).stream()
                .map(search -> toResponse(search, counts.getOrDefault(search.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<SavedSearchResponse> get(UUID ownerId, UUID id) {
        return searches.findByIdAndOwnerId(id, ownerId)
                .map(search -> toResponse(search, newMatchCounts(ownerId).getOrDefault(id, 0L)));
    }

    @Transactional
    public SavedSearchResponse create(UUID ownerId, SaveSearchRequest request) {
        var search = searches.save(SavedSearch.create(ownerId, settings(ownerId, request), Instant.now(clock)));
        return toResponse(search, 0);
    }

    /** Empty when the search isn't the owner's. */
    @Transactional
    public Optional<SavedSearchResponse> update(UUID ownerId, UUID id, SaveSearchRequest request) {
        if (request.version() == null) throw new InvalidSearchException("version", "Send the version you loaded.");
        var found = searches.findByIdAndOwnerId(id, ownerId);
        if (found.isEmpty()) return Optional.empty();
        var search = found.get();
        if (search.getVersion() != request.version()) {
            throw new OptimisticLockingFailureException(String.format(
                    "Saved search %s has version %d, which does not match version %d", id, search.getVersion(), request.version()));
        }
        search.apply(settings(ownerId, request), Instant.now(clock));
        searches.flush();
        return Optional.of(toResponse(search, newMatchCounts(ownerId).getOrDefault(id, 0L)));
    }

    /** Its matches stay, so dismissed postings stay dismissed and saved ones keep their link. */
    @Transactional
    public boolean delete(UUID ownerId, UUID id) {
        var found = searches.findByIdAndOwnerId(id, ownerId);
        found.ifPresent(searches::delete);
        return found.isPresent();
    }

    private SavedSearch.Settings settings(UUID ownerId, SaveSearchRequest r) {
        ZoneId zone;
        try {
            zone = ZoneId.of(r.timeZone().strip());
        } catch (DateTimeException ex) {
            throw new InvalidSearchException("timeZone", "Unknown time zone: " + r.timeZone());
        }
        var sources = r.sources() == null ? List.<String>of() : r.sources().stream().map(String::strip).distinct().toList();
        for (var source : sources) {
            if (!postings.isKnownSource(source)) throw new InvalidSearchException("sources", "Unknown source: " + source);
        }
        if (r.resumeDocumentId() != null && documents.find(ownerId, null, r.resumeDocumentId()).isEmpty()) {
            throw new InvalidSearchException("resumeDocumentId", "Pick a resume from your library.");
        }
        var exclude = r.excludeWords() == null ? List.<String>of() : r.excludeWords().stream().map(String::strip).distinct().toList();
        return new SavedSearch.Settings(r.name(), r.keywords(), exclude, r.location(), Boolean.TRUE.equals(r.remoteOnly()),
                r.salaryMin(), sources, r.postedWithinDays() != null ? r.postedWithinDays() : DEFAULT_POSTED_WITHIN_DAYS,
                r.resumeDocumentId(), r.minScore(), r.active() == null || r.active(), r.runDays(), r.runHour(), zone);
    }

    private Map<UUID, Long> newMatchCounts(UUID ownerId) {
        var counts = new HashMap<UUID, Long>();
        for (var row : matches.countNewBySearch(ownerId)) counts.put((UUID) row[0], ((Number) row[1]).longValue());
        return counts;
    }

    private SavedSearchResponse toResponse(SavedSearch s, long newMatches) {
        var lastRun = runs.findFirstBySavedSearchIdOrderByStartedAtDesc(s.getId())
                .map(run -> new SearchRunResponse(run.getStartedAt(), run.getFinishedAt(), run.getGathered(),
                        run.getNewMatches(), run.getScored(), run.getNotes()))
                .orElse(null);
        return new SavedSearchResponse(s.getId(), s.getName(), s.getKeywords(), s.getExcludeWords(), s.getLocation(),
                s.isRemoteOnly(), s.getSalaryMin(), s.getSources(), s.getPostedWithinDays(), s.getResumeDocumentId(),
                s.getMinScore(), s.isActive(), s.getRunDays().stream().sorted().toList(), s.getRunHour(),
                s.getTimeZone().getId(), s.getNextRunAt(), s.getLastRunAt(), s.isRunning(Instant.now(clock)),
                newMatches, lastRun, s.getVersion(), s.getCreatedAt());
    }
}
