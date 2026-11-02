package dev.beacon.infrastructure.persistence;

import dev.beacon.domain.searches.SearchRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Reached only through a saved search already checked to belong to the user. */
public interface SearchRunRepository extends JpaRepository<SearchRun, UUID> {

    Optional<SearchRun> findFirstBySavedSearchIdOrderByStartedAtDesc(UUID savedSearchId);
}
