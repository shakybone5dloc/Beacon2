package dev.beacon.contracts.checkin;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Size;

/**
 * @param recipient who it's addressed to: a person's name or a team; blank for the default team
 * @param body      the form's current text, for the AI to improve on
 */
public record CheckInDraftRequest(@Nullable @Size(max = 200) String recipient,
                                  @Nullable @Size(max = 10000) String body) {}
