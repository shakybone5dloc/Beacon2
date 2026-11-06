package dev.beacon.contracts.study;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param version the card's version as loaded, when editing; leave out for a new card */
public record SaveCardRequest(@NotBlank @Size(max = 2000) String question, @Nullable @Size(max = 10_000) String answer,
                              @Nullable Long version) {}
