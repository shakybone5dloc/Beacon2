package dev.beacon.contracts.resumes;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * @param label        e.g. "Backend Java" or "Test Engineering"
 * @param targetTitles the titles this resume is aimed at
 * @param version      the version the edit started from
 */
public record UpdateResumeRequest(
        @NotBlank @Size(max = 100) String label,
        @Nullable @Size(max = 10) List<@Size(max = 100) String> targetTitles,
        @Nullable @Size(max = 50) List<@Size(max = 60) String> skills,
        @Nullable @Min(0) @Max(60) Integer yearsExperience,
        @NotNull Long version
) {
}
