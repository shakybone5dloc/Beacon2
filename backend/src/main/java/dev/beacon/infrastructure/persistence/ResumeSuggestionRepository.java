package dev.beacon.infrastructure.persistence;

import dev.beacon.domain.tailoring.ResumeSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResumeSuggestionRepository extends JpaRepository<ResumeSuggestion, UUID> {

    List<ResumeSuggestion> findAllByOwnerIdAndApplicationIdOrderByPositionAsc(UUID ownerId, UUID applicationId);

    Optional<ResumeSuggestion> findByIdAndOwnerIdAndApplicationId(UUID id, UUID ownerId, UUID applicationId);
}
