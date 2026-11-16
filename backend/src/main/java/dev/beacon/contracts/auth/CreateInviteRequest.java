package dev.beacon.contracts.auth;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * @param note who it's for, e.g. "Pat, from the LinkedIn post"
 * @param days how long the code works; 14 when left out
 */
public record CreateInviteRequest(@Nullable @Size(max = 200) String note,
                                  @Nullable @Min(1) @Max(90) Integer days) {}
