package dev.beacon.infrastructure.persistence;

import dev.beacon.domain.digest.DigestSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DigestSettingsRepository extends JpaRepository<DigestSettings, UUID> {

    List<DigestSettings> findAllByEnabledTrue();
}
