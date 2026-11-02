package dev.beacon.infrastructure.persistence;

import dev.beacon.domain.fit.ApplicationFit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Reached only through an application already checked to belong to the user. */
public interface ApplicationFitRepository extends JpaRepository<ApplicationFit, UUID> {
}
