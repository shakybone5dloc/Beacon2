package dev.beacon.contracts.companies;

import dev.beacon.domain.companies.CompanyType;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/** @param type null for an employer */
public record CreateCompanyRequest(
        @NotBlank @Size(max = 200) String name,
        @Nullable CompanyType type,
        @Nullable @URL @Size(max = 2048) String website
) {
}
