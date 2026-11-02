package dev.beacon.infrastructure.persistence;

import dev.beacon.domain.searches.SavedSearch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Owner-scoped finders, plus the scheduler's query across all users. */
public interface SavedSearchRepository extends JpaRepository<SavedSearch, UUID> {

    List<SavedSearch> findAllByOwnerIdOrderByCreatedAtAsc(UUID ownerId);

    Optional<SavedSearch> findByIdAndOwnerId(UUID id, UUID ownerId);

    /** Active searches whose next run is due and that aren't already running (or whose run went stale). */
    @Query("""
            select s.id from SavedSearch s
            where s.active = true and s.nextRunAt <= :now
              and (s.runningSince is null or s.runningSince < :staleBefore)
            order by s.nextRunAt
            """)
    List<UUID> findDue(Instant now, Instant staleBefore);
}
