package dev.beacon.contracts.checkin;


import java.util.List;

/**
 * What the check-in form starts with.
 *
 * @param recipients  the people on the application
 * @param defaultTeam who it goes to when there's nobody to name, e.g. "Hiring team"
 * @param body        the template filled in, opening with "Hello,"
 * @param sendFrom    the address the user sends from
 * @param gmail       true when that's a Gmail address, so it can open in Gmail as that account
 * @param aiAvailable whether the user has a key to draft with AI
 */
public record CheckInResponse(List<CheckInRecipient> recipients, String defaultTeam, String subject, String body,
                              String sendFrom, boolean gmail, boolean aiAvailable) {}
