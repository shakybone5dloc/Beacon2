package dev.beacon.contracts.stories;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * @param notes            rough notes on one story to draft; leave out to get a few suggested from the resume
 * @param resumeDocumentId the resume to draw on; the library's default when left out
 */
public record DraftStoriesRequest(@Nullable @Size(max = 10_000) String notes, @Nullable UUID resumeDocumentId) {}
