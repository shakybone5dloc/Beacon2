package dev.beacon.contracts.auth;

/**
 * @param inviteOnly whether signing up needs an invite code
 * @param passwordReset whether this server can email password-reset links (it has mail set up)
 */
public record RegistrationResponse(boolean inviteOnly, boolean passwordReset) {}
