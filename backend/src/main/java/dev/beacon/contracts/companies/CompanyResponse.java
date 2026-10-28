package dev.beacon.contracts.companies;

import dev.beacon.domain.companies.CompanyType;
import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** @param applicationCount how many of the user's applications are with this company */
public record CompanyResponse(
        UUID id,
        String name,
        CompanyType type,
        @Nullable String website,
        List<String> emailDomains,
        @Nullable String notes,
        long applicationCount,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
}
