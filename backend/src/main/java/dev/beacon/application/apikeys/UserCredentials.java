package dev.beacon.application.apikeys;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** A user's saved provider keys, as the features that call providers need them. */
public interface UserCredentials {

    /** Ids of the providers this user has saved a key for. */
    Set<String> savedProviders(UUID userId);

    /**
     * The saved fields for one provider, e.g. {"apiKey": "..."}. Empty when none are saved.
     * Throws {@link dev.beacon.infrastructure.security.KeyCipher.UnreadableKeyException} when
     * the saved key can no longer be decrypted.
     */
    Optional<Map<String, String>> credentials(UUID userId, String providerId);
}
