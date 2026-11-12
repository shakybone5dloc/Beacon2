package dev.beacon.contracts.checkin;

import dev.beacon.domain.users.MailClient;

import java.util.List;

/**
 * What the check-in form starts with.
 *
 * @param recipients  the people on the application
 * @param defaultTeam who it goes to when there's nobody to name, e.g. "Hiring team"
 * @param body        the template filled in, opening with "Hello,"
 * @param mailClient  where the user's email opens
 * @param aiAvailable whether the user has a key to draft with AI
 */
public record CheckInResponse(List<CheckInRecipient> recipients, String defaultTeam, String subject, String body,
                              MailClient mailClient, boolean aiAvailable) {}
