package dev.beacon.contracts.digest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** @param version the settings' version as loaded; leave out the first time */
public record SaveDigestSettingsRequest(
        @NotNull Boolean enabled,
        @Schema(allowableValues = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"})
        @NotBlank String dayOfWeek,
        @NotNull @Min(0) @Max(23) Integer hour,
        @NotBlank String zone,
        @Nullable Long version
) {}
