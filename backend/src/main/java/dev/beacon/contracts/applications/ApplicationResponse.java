package dev.beacon.contracts.applications;

import dev.beacon.domain.applications.ApplicationStatus;
import dev.beacon.domain.applications.EmploymentType;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.List;

public record ApplicationResponse (
        UUID id,
        /** Null when the employer isn't disclosed. */
        @Nullable UUID companyId,
        /** The company's name; null when the employer isn't disclosed. */
        @Nullable String company,
        String role,
        @Nullable EmploymentType employmentType,
        /** For an undisclosed employer: the agency of a recruiter on the application, when there is one. */
        @Nullable String viaAgency,
        @Nullable String jobUrl,
        @Nullable String notes,
        @Nullable LocalDate applyBy,
        @Nullable LocalDate followUpOn,
        @Nullable String location,
        @Nullable Boolean remote,
        @Nullable Integer salaryMin,
        @Nullable Integer salaryMax,
        @Nullable String salaryCurrency,
        @Nullable String salaryPeriod,
        @Nullable String salaryText,
        @Nullable String source,
        @Nullable LocalDate postedOn,
        /** Null in list responses to keep the board light; present when fetching one application. */
        @Nullable String jobDescription,
        /** When the application first moved to APPLIED; null if it never has. */
        @Nullable Instant appliedAt,
        @Schema(implementation = ApplicationStatus.class)
        String status,
        Instant createdAt,
        Instant updatedAt,
        Instant statusChangedAt,
        Long version,
        @ArraySchema(schema = @Schema(implementation = ApplicationStatus.class))
        List<String> allowedTransitions
) {
}
