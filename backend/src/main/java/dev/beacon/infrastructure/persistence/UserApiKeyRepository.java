package dev.beacon.infrastructure.persistence;

import dev.beacon.domain.apikeys.UserApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserApiKeyRepository extends JpaRepository<UserApiKey, UserApiKey.Key> {

    List<UserApiKey> findAllByUserId(UUID userId);

    Optional<UserApiKey> findByUserIdAndProvider(UUID userId, String provider);
}
