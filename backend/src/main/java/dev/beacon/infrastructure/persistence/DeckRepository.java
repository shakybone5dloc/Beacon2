package dev.beacon.infrastructure.persistence;

import dev.beacon.domain.study.Deck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeckRepository extends JpaRepository<Deck, UUID> {

    List<Deck> findAllByOwnerIdOrderByUpdatedAtDesc(UUID ownerId);

    Optional<Deck> findByIdAndOwnerId(UUID id, UUID ownerId);

    Optional<Deck> findByApplicationIdAndOwnerId(UUID applicationId, UUID ownerId);
}
