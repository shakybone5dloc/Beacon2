package dev.beacon.contracts.apikeys;

import jakarta.annotation.Nullable;

import java.time.Instant;

/**
 * Whether a key is saved for a provider. The values themselves are never sent back.
 *
 * @param hint last few characters of the secret, e.g. "1a2b"
 */
public record ApiKeyResponse(
        String provider,
        boolean saved,
        @Nullable String hint,
        @Nullable Instant updatedAt
) {
}
