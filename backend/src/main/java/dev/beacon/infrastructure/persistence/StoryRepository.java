package dev.beacon.infrastructure.persistence;

import dev.beacon.domain.stories.Story;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoryRepository extends JpaRepository<Story, UUID> {

    List<Story> findAllByOwnerIdOrderByUpdatedAtDesc(UUID ownerId);

    Optional<Story> findByIdAndOwnerId(UUID id, UUID ownerId);
}
