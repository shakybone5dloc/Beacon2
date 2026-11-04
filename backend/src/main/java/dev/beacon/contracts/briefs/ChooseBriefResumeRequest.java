package dev.beacon.contracts.briefs;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ChooseBriefResumeRequest(@NotNull UUID resumeDocumentId) {}
