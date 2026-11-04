package dev.beacon.contracts.briefs;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** @param version the section's version as loaded; null for a section not saved yet */
public record SaveBriefSectionRequest(@NotNull @Size(max = 50_000) String content, @Nullable Long version) {}
