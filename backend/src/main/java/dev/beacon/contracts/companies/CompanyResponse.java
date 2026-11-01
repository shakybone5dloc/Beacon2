package dev.beacon.contracts.companies;

import dev.beacon.domain.companies.CareersPlatform;
import dev.beacon.domain.companies.CompanyType;
import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param applicationCount how many of the user's applications are with this company
 * @param careersPlatform  where it posts its jobs, when it's on the watchlist
 * @param careersBoardId   its board id on that platform, e.g. "acme" for boards.greenhouse.io/acme
 */
public record CompanyResponse(
        UUID id,
        String name,
        CompanyType type,
        @Nullable String website,
        List<String> emailDomains,
        @Nullable String notes,
        long applicationCount,
        @Nullable CareersPlatform careersPlatform,
        @Nullable String careersBoardId,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
}
