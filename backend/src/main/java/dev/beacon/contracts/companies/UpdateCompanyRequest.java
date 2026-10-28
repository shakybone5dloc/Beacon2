package dev.beacon.contracts.companies;

import dev.beacon.domain.companies.CompanyType;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.util.List;

/** @param emailDomains e.g. "acme.com"; null or empty clears them */
public record UpdateCompanyRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull CompanyType type,
        @Nullable @URL @Size(max = 2048) String website,
        @Nullable @Size(max = 20) List<@Pattern(regexp = "\\s*[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\s*",
                message = "must be a domain like acme.com") String> emailDomains,
        @Nullable @Size(max = 4000) String notes,
        @NotNull Long version
) {
}
