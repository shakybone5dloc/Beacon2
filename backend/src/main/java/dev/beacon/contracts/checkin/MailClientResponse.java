package dev.beacon.contracts.checkin;

import dev.beacon.domain.users.MailClient;

/** Where the user's "Email" buttons open a message. */
public record MailClientResponse(MailClient mailClient) {}
