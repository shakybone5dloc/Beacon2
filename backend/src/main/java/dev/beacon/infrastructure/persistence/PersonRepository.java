package dev.beacon.infrastructure.persistence;

import dev.beacon.domain.people.Person;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Every finder takes the owner, so one user's people can't leak into another's queries. */
public interface PersonRepository extends JpaRepository<Person, UUID> {

    @EntityGraph(attributePaths = "company")
    Optional<Person> findByIdAndOwnerId(UUID id, UUID ownerId);

    @EntityGraph(attributePaths = "company")
    List<Person> findAllByOwnerIdOrderByNameAsc(UUID ownerId);

    /** For the type-ahead. */
    @EntityGraph(attributePaths = "company")
    List<Person> findTop10ByOwnerIdAndNameContainingIgnoreCaseOrderByNameAsc(UUID ownerId, String query);

    @EntityGraph(attributePaths = "company")
    List<Person> findAllByOwnerIdAndCompanyIdOrderByNameAsc(UUID ownerId, UUID companyId);
}
