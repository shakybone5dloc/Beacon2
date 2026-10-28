package dev.beacon.contracts.contacts;

import dev.beacon.domain.contacts.ContactChannel;
import dev.beacon.domain.contacts.ContactDirection;

import java.time.Instant;
import java.util.UUID;

/** What create and update contact requests share; the records' accessors implement it. */
public interface ContactFields {
    UUID personId();
    UUID applicationId();
    ContactChannel channel();
    ContactDirection direction();
    Instant occurredAt();
    String body();
}
