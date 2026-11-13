package dev.beacon.contracts.checkin;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** @param sendFrom the address to send from; null or blank to use the sign-in address */
public record SendFromRequest(@Nullable @Email @Size(max = 320) String sendFrom) {}
