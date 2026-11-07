package dev.beacon.contracts.tailoring;

import jakarta.annotation.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The application's resume: the one sent with it, and the edits suggested to tailor one to its posting.
 *
 * @param sentResumeDocumentId the resume sent with the application; null when not recorded
 * @param resumeDocumentId     the resume the newest suggestions are for; null when there are none
 * @param resumeLabel          its label or file name
 * @param configured           whether the user has an AI key to ask for suggestions with
 */
public record TailoringResponse(
        @Nullable UUID sentResumeDocumentId,
        @Nullable UUID resumeDocumentId,
        @Nullable String resumeLabel,
        List<ResumeSuggestionResponse> suggestions,
        boolean configured
) {}
