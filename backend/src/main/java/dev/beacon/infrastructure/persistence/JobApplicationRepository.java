package dev.beacon.infrastructure.persistence;

import dev.beacon.domain.applications.JobApplication;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Every finder takes the owner, so one user's applications can't leak into another's queries. */
public interface JobApplicationRepository extends JpaRepository<JobApplication, UUID> {

    @EntityGraph(attributePaths = "company")
    Optional<JobApplication> findByIdAndOwnerId(UUID id, UUID ownerId);

    /** Loads each application's company in the same query, so the board doesn't run one query per card. */
    @EntityGraph(attributePaths = "company")
    List<JobApplication> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    @EntityGraph(attributePaths = "company")
    List<JobApplication> findAllByOwnerIdAndCompanyIdOrderByCreatedAtDesc(UUID ownerId, UUID companyId);

    /** Application counts per company: [company id, count]. */
    @Query("""
            select a.company.id, count(a) from JobApplication a
            where a.ownerId = :ownerId and a.company is not null
            group by a.company.id
            """)
    List<Object[]> countByCompany(UUID ownerId);

    boolean existsByIdAndOwnerId(UUID id, UUID ownerId);
}
