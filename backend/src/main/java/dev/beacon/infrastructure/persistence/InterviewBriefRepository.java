package dev.beacon.infrastructure.persistence;

import dev.beacon.domain.briefs.InterviewBrief;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface InterviewBriefRepository extends JpaRepository<InterviewBrief, UUID> {

    Optional<InterviewBrief> findByApplicationIdAndOwnerId(UUID applicationId, UUID ownerId);

    /**
     * Creates the application's brief unless it has one. Several sections written at once for
     * a new brief all get here: Postgres makes the later inserts wait, then skip, without an error.
     */
    @Modifying
    @Query(value = """
            insert into interview_briefs (id, owner_id, application_id, created_at, updated_at)
            values (:id, :ownerId, :applicationId, :now, :now)
            on conflict (application_id) do nothing
            """, nativeQuery = true)
    int insertIfMissing(UUID id, UUID ownerId, UUID applicationId, Instant now);
}
