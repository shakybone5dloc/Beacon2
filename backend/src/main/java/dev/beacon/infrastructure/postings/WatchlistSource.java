package dev.beacon.infrastructure.postings;

import dev.beacon.application.postings.CareersBoardClients;
import dev.beacon.application.postings.JobPosting;
import dev.beacon.application.postings.JobSource;
import dev.beacon.application.postings.PostingMatcher;
import dev.beacon.application.postings.PostingQuery;
import dev.beacon.infrastructure.persistence.CompanyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Open roles from the careers boards of the companies the searching user watches.
 * The one source that depends on who is searching, so it overrides the user-aware search.
 */
@Component
public class WatchlistSource implements JobSource {

    private static final Logger log = LoggerFactory.getLogger(WatchlistSource.class);

    private final CompanyRepository companies;
    private final CareersBoardClients clients;

    public WatchlistSource(CompanyRepository companies, CareersBoardClients clients) {
        this.companies = companies;
        this.clients = clients;
    }

    @Override public String id() { return "watchlist"; }
    @Override public String name() { return "Company watchlist"; }
    @Override public String coverage() { return "Open roles from the careers pages of companies you watch."; }
    @Override public String attributionUrl() { return "/companies"; }

    @Override
    public List<JobPosting> search(PostingQuery query, Map<String, String> credentials) {
        throw new UnsupportedOperationException("The watchlist needs the signed-in user.");
    }

    @Override
    public List<JobPosting> search(UUID userId, PostingQuery query, Map<String, String> credentials) {
        var found = new ArrayList<JobPosting>();
        for (var company : companies.findAllByOwnerIdAndCareersBoardIdIsNotNullOrderByNameAsc(userId)) {
            var board = company.getCareersBoard().orElseThrow();
            try {
                clients.forPlatform(board.platform()).openings(board.boardId(), company.getName()).stream()
                        .filter(p -> matches(p, query))
                        .forEach(found::add);
            } catch (RuntimeException ex) {
                // One company's board being down or renamed shouldn't sink the others.
                log.warn("Careers board {} for {} failed", board, company.getName(), ex);
            }
        }
        return found;
    }

    /**
     * Like {@link PostingMatcher#matches}, but keywords also match the full description:
     * careers boards always include it, and titles rarely name the stack ("Java").
     */
    private static boolean matches(JobPosting posting, PostingQuery query) {
        var texts = new ArrayList<String>(posting.tags());
        texts.addAll(Arrays.asList(posting.title(), posting.company(), posting.snippet(), posting.description()));
        return PostingMatcher.matchesKeywords(query.keywords(), texts)
                && PostingMatcher.matches(posting, new PostingQuery("", query.location(), query.remoteOnly()));
    }
}
