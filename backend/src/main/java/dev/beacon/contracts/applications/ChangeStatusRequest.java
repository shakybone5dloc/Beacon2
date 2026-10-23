package dev.beacon.contracts.applications;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

public record ChangeStatusRequest(
        @NotBlank String status,
        @NotNull Long version
) {
}
