package dev.beacon.application.applications;

import dev.beacon.application.companies.CompanyService;
import dev.beacon.contracts.applications.ApplicationResponse;
import dev.beacon.contracts.applications.CreateApplicationRequest;
import dev.beacon.contracts.applications.FollowUpRequest;
import dev.beacon.contracts.applications.StatusChangeResponse;
import dev.beacon.contracts.applications.UpdateApplicationRequest;
import dev.beacon.domain.applications.ApplicationStatus;
import dev.beacon.domain.applications.JobApplication;
import dev.beacon.infrastructure.persistence.JobApplicationRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Applications belong to one user. Every method takes the owner's id, and another
 * user's application behaves exactly like one that doesn't exist.
 */
@Service
public class ApplicationService {

    private final JobApplicationRepository repository;
    private final CompanyService companies;
    private final Clock clock;

    public ApplicationService(JobApplicationRepository repository, CompanyService companies, Clock clock) {
        this.repository = repository;
        this.companies = companies;
        this.clock = clock;
    }

    /** Throws {@link CompanyService.UnknownCompanyException} for a companyId that isn't the owner's. */
    @Transactional
    public ApplicationResponse create(UUID ownerId, CreateApplicationRequest request) {
        var now = Instant.now(clock);
        var application = JobApplication.create(
                ownerId,
                companies.resolve(ownerId, request, now),
                request.role(),
                request.jobUrl(),
                request.notes(),
                now
        );
        application.setApplyBy(request.applyBy(), now);
        application.scheduleFollowUp(request.followUpOn(), now);
        application.setPostingDetails(ApplicationMappings.toPostingDetails(request), now);
        application.setJobDescription(request.jobDescription(), now);

        repository.save(application);
        return ApplicationMappings.toResponse(application);
    }

    @Transactional(readOnly = true)
    public Optional<ApplicationResponse> getById(UUID ownerId, UUID id) {
        return repository.findByIdAndOwnerId(id, ownerId).map(ApplicationMappings::toResponse);
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> list(UUID ownerId) {
        return repository.findAllByOwnerIdOrderByCreatedAtDesc(ownerId)
                .stream()
                .map(ApplicationMappings::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<List<StatusChangeResponse>> history(UUID ownerId, UUID id) {
        return repository.findByIdAndOwnerId(id, ownerId).map(application -> application.getStatusHistory()
                .stream()
                .map(ApplicationMappings::toResponse)
                .toList());
    }

    @Transactional
    public Optional<ApplicationResponse> changeStatus(UUID ownerId, UUID id, ApplicationStatus newStatus, long expectedVersion) {
        var found = repository.findByIdAndOwnerId(id, ownerId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        var application = found.get();
        requireVersion(application, expectedVersion);

        application.changeStatus(newStatus, Instant.now(clock));

        repository.flush();
        return Optional.of(ApplicationMappings.toResponse(application));
    }

    @Transactional
    public Optional<ApplicationResponse> update(UUID ownerId, UUID id, UpdateApplicationRequest request) {
        var found = repository.findByIdAndOwnerId(id, ownerId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        var application = found.get();
        requireVersion(application, request.version());

        var now = Instant.now(clock);
        application.updateDetails(
                companies.resolve(ownerId, request, now),
                request.role(),
                request.jobUrl(),
                request.notes(),
                now
        );
        application.setApplyBy(request.applyBy(), now);
        application.scheduleFollowUp(request.followUpOn(), now);
        application.setPostingDetails(ApplicationMappings.toPostingDetails(request), now);
        application.setJobDescription(request.jobDescription(), now);

        repository.flush();
        return Optional.of(ApplicationMappings.toResponse(application));
    }

    @Transactional
    public Optional<ApplicationResponse> scheduleFollowUp(UUID ownerId, UUID id, FollowUpRequest request) {
        var found = repository.findByIdAndOwnerId(id, ownerId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        var application = found.get();
        requireVersion(application, request.version());

        application.scheduleFollowUp(request.followUpOn(), Instant.now(clock));

        repository.flush();
        return Optional.of(ApplicationMappings.toResponse(application));
    }

    @Transactional
    public boolean delete(UUID ownerId, UUID id) {
        var found = repository.findByIdAndOwnerId(id, ownerId);
        found.ifPresent(repository::delete);
        return found.isPresent();
    }

    private static void requireVersion(JobApplication application, long expectedVersion) {
        if (application.getVersion().longValue() != expectedVersion) {
            throw new OptimisticLockingFailureException(String.format("Application with id %s has version %d, which does not match version %d", application.getId(), application.getVersion(), expectedVersion));
        }
    }
}
