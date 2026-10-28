package dev.beacon.infrastructure.persistence;

import dev.beacon.domain.companies.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Every finder takes the owner, so one user's companies can't leak into another's queries. */
public interface CompanyRepository extends JpaRepository<Company, UUID> {

    Optional<Company> findByIdAndOwnerId(UUID id, UUID ownerId);

    Optional<Company> findByOwnerIdAndNormalizedName(UUID ownerId, String normalizedName);

    List<Company> findAllByOwnerIdOrderByNameAsc(UUID ownerId);

    /** For the type-ahead: {@code query} is already normalized. */
    List<Company> findTop10ByOwnerIdAndNormalizedNameContainingOrderByNameAsc(UUID ownerId, String query);
}
