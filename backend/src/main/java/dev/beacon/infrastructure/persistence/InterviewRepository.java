package dev.beacon.infrastructure.persistence;

import dev.beacon.domain.interviews.Interview;
import dev.beacon.domain.interviews.InterviewOutcome;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Interviews. Callers check ownership of the application first, or use the finder that takes the owner. */
public interface InterviewRepository extends JpaRepository<Interview, UUID> {

    @EntityGraph(attributePaths = {"interviewers", "application", "application.company"})
    Optional<Interview> findByIdAndOwnerId(UUID id, UUID ownerId);

    /** One application's interviews, earliest first, with their interviewers. */
    @EntityGraph(attributePaths = "interviewers")
    List<Interview> findAllByApplicationIdOrderByStartsAtAsc(UUID applicationId);

    /** Interviews a person is on, so they can be taken off before the person is deleted. */
    List<Interview> findAllByInterviewersId(UUID personId);

    /** Removes an application's interviews, through JPA so loaded ones don't go stale. */
    void deleteAllByApplicationId(UUID applicationId);

    /**
     * For each of these applications, its next interview after {@code now} and its last
     * one at or before it, cancelled ones left out: [application id, next, last].
     */
    @Query("""
            select i.application.id,
                   min(case when i.startsAt > :now then i.startsAt end),
                   max(case when i.startsAt <= :now then i.startsAt end)
            from Interview i
            where i.application.id in :applicationIds
              and (i.outcome is null or i.outcome <> :cancelled)
            group by i.application.id
            """)
    List<Object[]> findNextAndLast(Collection<UUID> applicationIds, Instant now, InterviewOutcome cancelled);
}
