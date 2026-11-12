package dev.beacon.contracts.checkin;

import dev.beacon.domain.people.PersonRole;
import jakarta.annotation.Nullable;

import java.util.UUID;

/** Someone on the application a check-in can go to; {@code email} is null when none is saved. */
public record CheckInRecipient(UUID personId, String name, @Nullable String email, PersonRole role) {}
