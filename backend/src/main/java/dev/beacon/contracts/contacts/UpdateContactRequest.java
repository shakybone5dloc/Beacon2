package dev.beacon.contracts.contacts;

import dev.beacon.domain.contacts.ContactChannel;
import dev.beacon.domain.contacts.ContactDirection;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** Same fields as {@link CreateContactRequest}, plus the version the edit started from. */
public record UpdateContactRequest(
        @NotNull UUID personId,
        @Nullable UUID applicationId,
        @NotNull ContactChannel channel,
        @NotNull ContactDirection direction,
        @NotNull Instant occurredAt,
        @Nullable @Size(max = 20000) String body,
        @NotNull Long version
) implements ContactFields {
}
