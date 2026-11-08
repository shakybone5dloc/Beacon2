package dev.beacon.contracts.home;

import java.util.UUID;

/**
 * An application with something small left to do.
 *
 * @param employer the company's name, or "Undisclosed client"
 * @param count    how many of the thing are left, such as open resume suggestions; 1 when it's a single step
 */
public record HomeTodoResponse(UUID applicationId, String employer, String role, int count) {}
