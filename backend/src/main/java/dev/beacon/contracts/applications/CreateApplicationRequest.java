package dev.beacon.contracts.applications;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.time.LocalDate;

public record CreateApplicationRequest (
    @NotBlank @Size(max = 200) String company,
    @NotBlank @Size(max = 200) String role,
    @URL @Size(max = 2048) String jobUrl,
    @Nullable @Size(max = 4000) String notes,
    @Nullable LocalDate applyBy,
    @Nullable LocalDate followUpOn
    ) {
}
