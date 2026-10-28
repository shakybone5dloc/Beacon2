package dev.beacon.contracts.applications;

import dev.beacon.domain.applications.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

public record ChangeStatusRequest(
        @Schema(implementation = ApplicationStatus.class)
        @NotBlank String status,
        @NotNull Long version
) {
}
