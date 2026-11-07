package dev.beacon.contracts.tailoring;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.UUID;

/** One edit the AI suggested to tailor the resume to this posting. */
public record ResumeSuggestionResponse(
        UUID id,
        @Schema(allowableValues = {"ADD", "EMPHASIZE", "REWORD", "MOVE", "CUT"}) String kind,
        String section,
        String suggestion,
        String reason,
        @Nullable String example,
        @Schema(allowableValues = {"OPEN", "DONE", "DISMISSED"}) String state,
        Instant createdAt
) {}
