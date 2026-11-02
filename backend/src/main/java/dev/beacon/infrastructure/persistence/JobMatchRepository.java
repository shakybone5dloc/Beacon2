package dev.beacon.infrastructure.persistence;

import dev.beacon.domain.searches.JobMatch;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Every finder takes the owner. */
public interface JobMatchRepository extends JpaRepository<JobMatch, UUID> {

    Optional<JobMatch> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<JobMatch> findAllByOwnerIdAndStatusOrderByFoundAtDesc(UUID ownerId, JobMatch.Status status);

    long countByOwnerIdAndStatus(UUID ownerId, JobMatch.Status status);

    /** Which of these keys the user has already seen, whatever became of them. */
    @Query("select m.matchKey from JobMatch m where m.ownerId = :ownerId and m.matchKey in :keys")
    Set<String> findKnownKeys(UUID ownerId, Collection<String> keys);

    /** A search's new matches that haven't been scored, newest postings first. */
    @Query("""
            select m from JobMatch m
            where m.ownerId = :ownerId and m.savedSearchId = :searchId
              and m.status = dev.beacon.domain.searches.JobMatch.Status.NEW and m.score is null
            order by m.postedAt desc nulls last, m.foundAt desc
            """)
    List<JobMatch> findUnscored(UUID ownerId, UUID searchId, Limit limit);

    @Query("""
            select m.savedSearchId, count(m) from JobMatch m
            where m.ownerId = :ownerId and m.status = dev.beacon.domain.searches.JobMatch.Status.NEW
              and m.savedSearchId is not null
            group by m.savedSearchId
            """)
    List<Object[]> countNewBySearch(UUID ownerId);
}
