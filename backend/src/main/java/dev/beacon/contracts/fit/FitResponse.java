package dev.beacon.contracts.fit;

import jakarta.annotation.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** How well a resume fits a posting. */
public record FitResponse(
        UUID id,
        /** 0 to 100. */
        int score,
        List<String> matchedSkills,
        List<String> gaps,
        /** Hard requirements the resume doesn't clearly meet: clearance, sponsorship, on-site location... */
        List<String> dealbreakers,
        String rationale,
        /** The deadline the posting states, when it states one. */
        @Nullable LocalDate applyBy,
        UUID resumeDocumentId,
        /** The resume's label, or its file name for a resume attached to one application. */
        String resumeLabel,
        Instant scoredAt,
        /** False when the job description or the resume's text has changed since; check again for a fresh score. */
        boolean current
) {
}
