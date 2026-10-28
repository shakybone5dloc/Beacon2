package dev.beacon.contracts.people;

import dev.beacon.domain.people.PersonRole;
import dev.beacon.domain.people.RecruiterType;
import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * @param role          their usual role
 * @param recruiterType only for recruiters
 */
public record PersonResponse(
        UUID id,
        String name,
        @Nullable UUID companyId,
        @Nullable String company,
        @Nullable String title,
        PersonRole role,
        @Nullable RecruiterType recruiterType,
        @Nullable String email,
        @Nullable String phone,
        @Nullable String linkedinUrl,
        @Nullable String notes,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
}
