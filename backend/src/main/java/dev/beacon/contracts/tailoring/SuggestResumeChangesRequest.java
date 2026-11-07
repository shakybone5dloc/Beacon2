package dev.beacon.contracts.tailoring;

import jakarta.annotation.Nullable;

import java.util.UUID;

/** @param resumeDocumentId the resume to tailor; null for the one the fit was checked with, else the default */
public record SuggestResumeChangesRequest(@Nullable UUID resumeDocumentId) {}
