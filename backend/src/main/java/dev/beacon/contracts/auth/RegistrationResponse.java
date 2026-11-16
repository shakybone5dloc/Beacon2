package dev.beacon.contracts.auth;

/** @param inviteOnly whether signing up needs an invite code */
public record RegistrationResponse(boolean inviteOnly) {}
