package dev.beacon.application.companies;

import dev.beacon.application.postings.CareersBoardClients;
import dev.beacon.application.postings.JobPosting;
import dev.beacon.application.postings.PostingSearchService;
import dev.beacon.contracts.companies.CompanyResponse;
import dev.beacon.contracts.postings.JobPostingResponse;
import dev.beacon.domain.companies.CareersBoard;
import dev.beacon.infrastructure.persistence.CompanyRepository;
import dev.beacon.infrastructure.postings.CareersPageFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The company watchlist: which companies' careers boards the user follows, and
 * what's open on them. Another user's company behaves as if it doesn't exist.
 */
@Service
public class CareersBoardService {

    private static final Logger log = LoggerFactory.getLogger(CareersBoardService.class);

    private final CompanyRepository companies;
    private final CompanyService companyService;
    private final CareersBoardClients clients;
    private final CareersPageFinder finder;
    private final Clock clock;

    public CareersBoardService(CompanyRepository companies, CompanyService companyService,
                               CareersBoardClients clients, CareersPageFinder finder, Clock clock) {
        this.companies = companies;
        this.companyService = companyService;
        this.clients = clients;
        this.finder = finder;
        this.clock = clock;
    }

    /**
     * Starts watching the board at {@code url}, after checking it can be read.
     * Empty when the company does not exist; throws {@link UnknownBoardException}
     * for a link Beacon can't use.
     */
    @Transactional
    public Optional<CompanyResponse> watch(UUID ownerId, UUID companyId, String url) {
        var found = companies.findByIdAndOwnerId(companyId, ownerId);
        if (found.isEmpty()) return Optional.empty();
        var company = found.get();
        var board = finder.find(url).orElseThrow(() -> new UnknownBoardException(
                "Paste a Greenhouse, Lever, Ashby, Workday or Oracle careers page link, like https://jobs.lever.co/acme."));
        try {
            clients.forPlatform(board.platform()).openings(board.boardId(), company.getName());
        } catch (RuntimeException ex) {
            log.info("Couldn't read careers board {}", board, ex);
            throw new UnknownBoardException("Couldn't read a careers board at that link. Check it opens the list of jobs.");
        }
        company.setCareersBoard(board, Instant.now(clock));
        companies.flush();
        return companyService.get(ownerId, companyId);
    }

    /** Empty when the company does not exist. */
    @Transactional
    public Optional<CompanyResponse> stopWatching(UUID ownerId, UUID companyId) {
        var found = companies.findByIdAndOwnerId(companyId, ownerId);
        if (found.isEmpty()) return Optional.empty();
        found.get().setCareersBoard(null, Instant.now(clock));
        companies.flush();
        return companyService.get(ownerId, companyId);
    }

    /**
     * What's open on the company's board, newest first; empty list when it isn't watched,
     * empty when the company does not exist. Throws {@link BoardUnavailableException}
     * when the board can't be read right now.
     */
    @Transactional(readOnly = true)
    public Optional<List<JobPostingResponse>> openings(UUID ownerId, UUID companyId) {
        return companies.findByIdAndOwnerId(companyId, ownerId).map(company -> company.getCareersBoard()
                .map(board -> read(board, company.getName()))
                .orElse(List.of()));
    }

    private List<JobPostingResponse> read(CareersBoard board, String companyName) {
        try {
            return clients.forPlatform(board.platform()).openings(board.boardId(), companyName).stream()
                    .sorted(Comparator.comparing(JobPosting::postedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .map(PostingSearchService::toResponse)
                    .toList();
        } catch (RuntimeException ex) {
            throw new BoardUnavailableException("Couldn't read " + companyName + "'s careers board right now.");
        }
    }

    /** A careers page link Beacon doesn't recognize, or whose board can't be read. */
    public static class UnknownBoardException extends RuntimeException {
        public UnknownBoardException(String message) {
            super(message);
        }
    }

    /** A watched board that can't be read at the moment. */
    public static class BoardUnavailableException extends RuntimeException {
        public BoardUnavailableException(String message) {
            super(message);
        }
    }
}
