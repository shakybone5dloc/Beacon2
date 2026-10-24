package dev.beacon.contracts.documents;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        /** Null for library documents. */
        UUID applicationId,
        String kind,
        String filename,
        String contentType,
        int sizeBytes,
        Instant createdAt
) {
}
