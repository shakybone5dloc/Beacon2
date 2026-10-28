package dev.beacon.contracts.people;

import dev.beacon.domain.people.PersonRole;
import jakarta.annotation.Nullable;

/** @param role their role on the application; null uses the person's usual role */
public record LinkPersonRequest(@Nullable PersonRole role) {
}
