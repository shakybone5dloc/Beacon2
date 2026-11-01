package dev.beacon.contracts.resumes;

import jakarta.annotation.Nullable;

/** @param text what Beacon read from the file; null when it has no readable text */
public record ResumeTextResponse(
        @Nullable String text
) {
}
