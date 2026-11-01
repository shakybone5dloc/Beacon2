package dev.beacon.contracts.resumes;

import dev.beacon.domain.resumes.ResumeProfile.TextStatus;
import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A library resume and what Beacon knows about it.
 *
 * @param documentId  the library document it describes; also how it's downloaded
 * @param isDefault   the one cover letters and fit scoring use unless told otherwise
 * @param textStatus  whether Beacon could read text from the file (READ) or not (UNREADABLE)
 */
public record ResumeResponse(
        UUID documentId,
        String label,
        boolean isDefault,
        String filename,
        String contentType,
        int sizeBytes,
        TextStatus textStatus,
        List<String> targetTitles,
        List<String> skills,
        @Nullable Integer yearsExperience,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
}
