package dev.beacon.contracts.contacts;

import dev.beacon.domain.contacts.ContactChannel;
import dev.beacon.domain.contacts.ContactDirection;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * @param personId      one of the user's people
 * @param applicationId one of the user's applications, when the contact was about one;
 *                      the person is added to it if they aren't on it yet
 * @param body          the pasted message, or notes from a call
 */
public record CreateContactRequest(
        @NotNull UUID personId,
        @Nullable UUID applicationId,
        @NotNull ContactChannel channel,
        @NotNull ContactDirection direction,
        @NotNull Instant occurredAt,
        @Nullable @Size(max = 20000) String body
) implements ContactFields {
}
