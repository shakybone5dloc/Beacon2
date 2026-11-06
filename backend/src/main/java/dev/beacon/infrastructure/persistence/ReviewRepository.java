package dev.beacon.infrastructure.persistence;

import dev.beacon.domain.study.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Reached only through a card already checked to belong to the user. */
public interface ReviewRepository extends JpaRepository<Review, UUID> {
}
