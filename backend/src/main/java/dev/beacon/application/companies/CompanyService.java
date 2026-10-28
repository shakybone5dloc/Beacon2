package dev.beacon.application.companies;

import dev.beacon.application.applications.ApplicationMappings;
import dev.beacon.contracts.applications.ApplicationResponse;
import dev.beacon.contracts.applications.CompanyChoice;
import dev.beacon.contracts.companies.CompanyResponse;
import dev.beacon.contracts.companies.UpdateCompanyRequest;
import dev.beacon.domain.companies.Company;
import dev.beacon.domain.companies.CompanyNames;
import dev.beacon.infrastructure.persistence.CompanyRepository;
import dev.beacon.infrastructure.persistence.JobApplicationRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Companies belong to one user. Every method takes the owner's id, and another
 * user's company behaves exactly like one that doesn't exist.
 */
@Service
public class CompanyService {

    private final CompanyRepository companies;
    private final JobApplicationRepository applications;
    private final Clock clock;

    public CompanyService(CompanyRepository companies, JobApplicationRepository applications, Clock clock) {
        this.companies = companies;
        this.applications = applications;
        this.clock = clock;
    }

    /** All of the user's companies by name, or up to ten whose name contains {@code query}. */
    @Transactional(readOnly = true)
    public List<CompanyResponse> list(UUID ownerId, String query) {
        var found = query == null || query.isBlank()
                ? companies.findAllByOwnerIdOrderByNameAsc(ownerId)
                : companies.findTop10ByOwnerIdAndNormalizedNameContainingOrderByNameAsc(ownerId, CompanyNames.normalize(query));
        var counts = applicationCounts(ownerId);
        return found.stream().map(c -> toResponse(c, counts.getOrDefault(c.getId(), 0L))).toList();
    }

    @Transactional(readOnly = true)
    public Optional<CompanyResponse> get(UUID ownerId, UUID id) {
        return companies.findByIdAndOwnerId(id, ownerId)
                .map(c -> toResponse(c, applicationCounts(ownerId).getOrDefault(c.getId(), 0L)));
    }

    /** Empty when the company does not exist. */
    @Transactional(readOnly = true)
    public Optional<List<ApplicationResponse>> applications(UUID ownerId, UUID id) {
        return companies.findByIdAndOwnerId(id, ownerId).map(company ->
                applications.findAllByOwnerIdAndCompanyIdOrderByCreatedAtDesc(ownerId, company.getId()).stream()
                        .map(ApplicationMappings::toSummary)
                        .toList());
    }

    /** Throws {@link NameTakenException} when another of the user's companies already has the name. */
    @Transactional
    public Optional<CompanyResponse> update(UUID ownerId, UUID id, UpdateCompanyRequest request) {
        var found = companies.findByIdAndOwnerId(id, ownerId);
        if (found.isEmpty()) return Optional.empty();
        var company = found.get();
        if (company.getVersion().longValue() != request.version()) {
            throw new OptimisticLockingFailureException(String.format(
                    "Company with id %s has version %d, which does not match version %d", id, company.getVersion(), request.version()));
        }

        var clash = companies.findByOwnerIdAndNormalizedName(ownerId, CompanyNames.normalize(request.name()));
        if (clash.isPresent() && !clash.get().getId().equals(id)) {
            throw new NameTakenException("You already have a company called " + clash.get().getName() + ".");
        }

        company.update(request.name(), request.type(), request.website(), request.emailDomains(), request.notes(), Instant.now(clock));
        companies.flush();
        return get(ownerId, id);
    }

    /**
     * The company an application should link to: the given id (which must be the
     * user's), else the user's company with that name, created if it's new. Null
     * when neither is given: the employer isn't disclosed.
     */
    @Transactional
    public Company resolve(UUID ownerId, CompanyChoice choice, Instant now) {
        if (choice.companyId() != null) {
            return companies.findByIdAndOwnerId(choice.companyId(), ownerId)
                    .orElseThrow(() -> new UnknownCompanyException("That company was not found."));
        }
        var name = choice.companyName();
        if (name == null || name.isBlank()) return null;
        return companies.findByOwnerIdAndNormalizedName(ownerId, CompanyNames.normalize(name))
                .orElseGet(() -> companies.save(Company.create(ownerId, name, now)));
    }

    private Map<UUID, Long> applicationCounts(UUID ownerId) {
        var counts = new HashMap<UUID, Long>();
        for (var row : applications.countByCompany(ownerId)) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }

    private static CompanyResponse toResponse(Company c, long applicationCount) {
        return new CompanyResponse(c.getId(), c.getName(), c.getType(), c.getWebsite(), c.getEmailDomains(), c.getNotes(),
                applicationCount, c.getVersion(), c.getCreatedAt(), c.getUpdatedAt());
    }

    public static class UnknownCompanyException extends RuntimeException {
        public UnknownCompanyException(String message) {
            super(message);
        }
    }

    public static class NameTakenException extends RuntimeException {
        public NameTakenException(String message) {
            super(message);
        }
    }
}
