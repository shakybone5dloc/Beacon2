package dev.beacon.contracts.checkin;

/**
 * The address the user sends Beacon-written emails from.
 *
 * @param sendFrom     the address in use: the one set, or else the sign-in address
 * @param signInEmail  the address the user signs in with, the default
 * @param gmail        true for a Gmail address: emails open in Gmail as that account;
 *                     otherwise they open in the device's mail app
 */
public record SendFromResponse(String sendFrom, String signInEmail, boolean gmail) {}
