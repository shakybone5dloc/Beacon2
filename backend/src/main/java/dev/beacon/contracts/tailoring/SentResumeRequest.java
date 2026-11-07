package dev.beacon.contracts.tailoring;

import jakarta.annotation.Nullable;

import java.util.UUID;

/** @param resumeDocumentId the resume sent with the application; null to clear it */
public record SentResumeRequest(@Nullable UUID resumeDocumentId) {}
