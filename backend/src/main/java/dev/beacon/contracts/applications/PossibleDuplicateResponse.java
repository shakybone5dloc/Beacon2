package dev.beacon.contracts.applications;

import dev.beacon.domain.applications.ApplicationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Another application that looks like the same job: same company, same title,
 * added within a few weeks. Being submitted twice for one job can disqualify a candidate.
 *
 * @param jobRole    its job title
 * @param recruiters the recruiters on it, as "Name (Agency)" or just the name
 */
public record PossibleDuplicateResponse(
        UUID applicationId,
        String company,
        String jobRole,
        ApplicationStatus status,
        Instant createdAt,
        List<String> recruiters
) {
}
