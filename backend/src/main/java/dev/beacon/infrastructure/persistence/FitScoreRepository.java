package dev.beacon.infrastructure.persistence;

import dev.beacon.domain.fit.FitScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Every finder takes the owner, so one user's scores can't leak into another's queries. */
public interface FitScoreRepository extends JpaRepository<FitScore, UUID> {

    Optional<FitScore> findByOwnerIdAndInputHash(UUID ownerId, String inputHash);

    Optional<FitScore> findByIdAndOwnerId(UUID id, UUID ownerId);
}
