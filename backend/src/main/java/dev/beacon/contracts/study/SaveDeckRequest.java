package dev.beacon.contracts.study;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param version the deck's version as loaded, when renaming; leave out for a new deck */
public record SaveDeckRequest(@NotBlank @Size(max = 200) String name, @Nullable Long version) {}
