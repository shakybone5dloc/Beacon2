package dev.beacon.application.applications;

import dev.beacon.contracts.applications.ApplicationResponse;
import dev.beacon.contracts.applications.CreateApplicationRequest;
import dev.beacon.domain.applications.JobApplication;
import dev.beacon.infrastructure.persistence.JobApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ApplicationService {

    private final JobApplicationRepository repository;
    private final Clock clock;

    public ApplicationService(JobApplicationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public ApplicationResponse create(CreateApplicationRequest request) {
        var application = JobApplication.create(
                request.company(),
                request.role(),
                request.jobUrl(),
                request.notes(),
                Instant.now(clock)
        );

        repository.save(application);
        return ApplicationMappings.toResponse(application);
    }

    @Transactional(readOnly = true)
    public Optional<ApplicationResponse> getById(UUID id) {
        return repository.findById(id).map(ApplicationMappings::toResponse);
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> list() {
        return repository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(ApplicationMappings::toResponse)
                .toList();
    }
}
