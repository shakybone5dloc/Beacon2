package dev.beacon.contracts.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param password the account's current password, to confirm it's really them */
public record DeleteAccountRequest(@NotBlank @Size(max = 200) String password) {}
