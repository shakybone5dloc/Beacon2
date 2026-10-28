package dev.beacon.application.applications;

import dev.beacon.application.companies.CompanyService;
import dev.beacon.contracts.applications.ApplicationResponse;
import dev.beacon.contracts.applications.CreateApplicationRequest;
import dev.beacon.contracts.applications.FollowUpRequest;
import dev.beacon.contracts.applications.StatusChangeResponse;
import dev.beacon.contracts.applications.UpdateApplicationRequest;
import dev.beacon.domain.applications.ApplicationStatus;
import dev.beacon.domain.applications.JobApplication;
import dev.beacon.domain.contacts.Contact;
import dev.beacon.domain.people.PersonRole;
import dev.beacon.infrastructure.persistence.ApplicationPersonRepository;
import dev.beacon.infrastructure.persistence.ContactRepository;
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
 * Applications belong to one user. Every method takes the owner's id, and another
 * user's application behaves exactly like one that doesn't exist.
 */
@Service
public class ApplicationService {

    private final JobApplicationRepository repository;
    private final ApplicationPersonRepository people;
    private final ContactRepository contacts;
    private final CompanyService companies;
    private final Clock clock;

    public ApplicationService(JobApplicationRepository repository, ApplicationPersonRepository people,
                              ContactRepository contacts, CompanyService companies, Clock clock) {
        this.repository = repository;
        this.people = people;
        this.contacts = contacts;
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
        application.setEmploymentType(request.employmentType(), now);
        application.setPostingDetails(ApplicationMappings.toPostingDetails(request), now);
        application.setJobDescription(request.jobDescription(), now);

        repository.save(application);
        return toResponse(application);
    }

    @Transactional(readOnly = true)
    public Optional<ApplicationResponse> getById(UUID ownerId, UUID id) {
        return repository.findByIdAndOwnerId(id, ownerId)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> list(UUID ownerId) {
        var found = repository.findAllByOwnerIdOrderByCreatedAtDesc(ownerId);
        var agencies = agencies(found);
        return found.stream()
                .map(app -> ApplicationMappings.toSummary(app, agencies.get(app.getId())))
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
        return Optional.of(toResponse(application));
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
        application.setEmploymentType(request.employmentType(), now);
        application.setPostingDetails(ApplicationMappings.toPostingDetails(request), now);
        application.setJobDescription(request.jobDescription(), now);

        repository.flush();
        return Optional.of(toResponse(application));
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
        return Optional.of(toResponse(application));
    }

    @Transactional
    public boolean delete(UUID ownerId, UUID id) {
        var found = repository.findByIdAndOwnerId(id, ownerId);
        found.ifPresent(application -> {
            // The people and their contact logs stay; only their links to this application go.
            people.deleteAllByApplicationId(application.getId());
            contacts.findAllByApplicationIdOrderByOccurredAtDesc(application.getId()).forEach(Contact::forgetApplication);
            repository.delete(application);
        });
        return found.isPresent();
    }

    private ApplicationResponse toResponse(JobApplication application) {
        return ApplicationMappings.toResponse(application, agencies(List.of(application)).get(application.getId()));
    }

    /**
     * For applications whose employer isn't disclosed: the company of the first recruiter
     * linked to each, by application id. One query however many applications there are.
     */
    private Map<UUID, String> agencies(List<JobApplication> found) {
        var undisclosed = found.stream().filter(app -> app.getCompany() == null).map(JobApplication::getId).toList();
        if (undisclosed.isEmpty()) return Map.of();
        var agencies = new HashMap<UUID, String>();
        for (var row : people.findCompanyNamesByRole(undisclosed, PersonRole.RECRUITER)) {
            agencies.putIfAbsent((UUID) row[0], (String) row[1]);
        }
        return agencies;
    }

    private static void requireVersion(JobApplication application, long expectedVersion) {
        if (application.getVersion().longValue() != expectedVersion) {
            throw new OptimisticLockingFailureException(String.format("Application with id %s has version %d, which does not match version %d", application.getId(), application.getVersion(), expectedVersion));
        }
    }
}
