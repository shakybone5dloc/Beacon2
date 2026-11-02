package dev.beacon.infrastructure.persistence;

import dev.beacon.domain.fit.ApplicationFit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Reached only through an application already checked to belong to the user. */
public interface ApplicationFitRepository extends JpaRepository<ApplicationFit, UUID> {

    /** Each application's latest score: [application id, score]. */
    @Query("""
            select l.applicationId, s.score from ApplicationFit l, FitScore s
            where s.id = l.fitScoreId and l.applicationId in :applicationIds
            """)
    List<Object[]> findScores(Collection<UUID> applicationIds);
}
