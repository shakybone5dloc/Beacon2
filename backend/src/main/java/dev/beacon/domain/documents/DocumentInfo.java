package dev.beacon.domain.documents;

import java.time.Instant;
import java.util.UUID;

/** A document without its bytes. */
public record DocumentInfo(
        UUID id,
        UUID applicationId,
        DocumentKind kind,
        String filename,
        String contentType,
        int sizeBytes,
        Instant createdAt
) {
}
