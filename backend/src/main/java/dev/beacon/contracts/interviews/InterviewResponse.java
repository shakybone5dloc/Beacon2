package dev.beacon.contracts.interviews;

import dev.beacon.domain.interviews.InterviewOutcome;
import dev.beacon.domain.interviews.InterviewType;
import jakarta.annotation.Nullable;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @param startsAt   the moment it starts
 * @param localStart the same moment as a wall-clock time in {@code timeZone}, for editing
 * @param outcome    null until known
 */
public record InterviewResponse(
        UUID id,
        UUID applicationId,
        Instant startsAt,
        Instant endsAt,
        LocalDateTime localStart,
        String timeZone,
        int durationMinutes,
        InterviewType type,
        @Nullable String location,
        List<InterviewerResponse> interviewers,
        @Nullable String prepNotes,
        @Nullable String debriefNotes,
        @Nullable InterviewOutcome outcome,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
    /** @param company where they work, when known */
    public record InterviewerResponse(UUID id, String name, @Nullable String company) { }
}
