package dev.beacon.contracts.tailoring;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record MarkSuggestionRequest(
        @Schema(allowableValues = {"OPEN", "DONE", "DISMISSED"}) @NotBlank String state
) {}
