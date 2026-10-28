package dev.beacon.contracts.applications;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.time.LocalDate;

public record CreateApplicationRequest (
    @NotBlank @Size(max = 200) String company,
    @NotBlank @Size(max = 200) String role,
    @Nullable @URL @Size(max = 2048) String jobUrl,
    @Nullable @Size(max = 4000) String notes,
    @Nullable LocalDate applyBy,
    @Nullable LocalDate followUpOn,
    @Nullable @Size(max = 500) String location,
    @Nullable Boolean remote,
    @Nullable @PositiveOrZero Integer salaryMin,
    @Nullable @PositiveOrZero Integer salaryMax,
    @Nullable @Pattern(regexp = "[A-Za-z]{3}", message = "must be a 3-letter currency code") String salaryCurrency,
    @Nullable @Pattern(regexp = "year|month|week|day|hour", message = "must be year, month, week, day or hour") String salaryPeriod,
    @Nullable @Size(max = 100) String salaryText,
    @Nullable @Size(max = 100) String source,
    @Nullable LocalDate postedOn,
    @Nullable @Size(max = 100_000) String jobDescription
    ) implements PostingFields {
}
