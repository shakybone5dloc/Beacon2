package dev.beacon.contracts.documents;

import dev.beacon.domain.documents.DocumentKind;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        /** Null for library documents. */
        UUID applicationId,
        @Schema(implementation = DocumentKind.class)
        String kind,
        String filename,
        String contentType,
        int sizeBytes,
        Instant createdAt
) {
}
