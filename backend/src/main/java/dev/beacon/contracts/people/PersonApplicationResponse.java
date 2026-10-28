package dev.beacon.contracts.people;

import dev.beacon.domain.applications.ApplicationStatus;
import dev.beacon.domain.people.PersonRole;
import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * An application a person is on.
 *
 * @param company   null when the employer isn't disclosed
 * @param jobRole   the job's title, e.g. "Java Engineer"
 * @param role      the person's role on this application
 */
public record PersonApplicationResponse(
        UUID applicationId,
        @Nullable String company,
        String jobRole,
        ApplicationStatus status,
        PersonRole role,
        Instant addedAt
) {
}
