package dev.beacon.contracts.people;

import dev.beacon.domain.people.PersonRole;

import java.time.Instant;

/**
 * Someone on an application.
 *
 * @param role their role on this application
 */
public record ApplicationPersonResponse(
        PersonResponse person,
        PersonRole role,
        Instant addedAt
) {
}
