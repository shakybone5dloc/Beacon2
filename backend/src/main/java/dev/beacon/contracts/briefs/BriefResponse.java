package dev.beacon.contracts.briefs;

import jakarta.annotation.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * An application's interview brief, every section in reading order, written or not.
 *
 * @param resumeDocumentId the resume sections are written from; null when the user has none Beacon can use
 * @param configured       true when the user has an AI key to write sections with
 */
public record BriefResponse(@Nullable UUID resumeDocumentId, @Nullable String resumeLabel, boolean configured,
                            List<BriefSectionResponse> sections) {}
