package dev.beacon.infrastructure.persistence;

import dev.beacon.domain.applications.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Every finder takes the owner, so one user's applications can't leak into another's queries. */
public interface JobApplicationRepository extends JpaRepository<JobApplication, UUID> {

    Optional<JobApplication> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<JobApplication> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    boolean existsByIdAndOwnerId(UUID id, UUID ownerId);
}
