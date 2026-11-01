package dev.beacon.infrastructure.persistence;

import dev.beacon.domain.resumes.ResumeProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Every finder takes the owner, so one user's resumes can't leak into another's queries. */
public interface ResumeProfileRepository extends JpaRepository<ResumeProfile, UUID> {

    /** The default first, then newest first. */
    List<ResumeProfile> findAllByOwnerIdOrderByDefaultResumeDescCreatedAtDesc(UUID ownerId);

    Optional<ResumeProfile> findByDocumentIdAndOwnerId(UUID documentId, UUID ownerId);

    Optional<ResumeProfile> findByOwnerIdAndDefaultResumeTrue(UUID ownerId);

    boolean existsByOwnerId(UUID ownerId);
}
