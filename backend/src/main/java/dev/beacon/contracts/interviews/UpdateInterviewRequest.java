package dev.beacon.contracts.interviews;

import dev.beacon.domain.interviews.InterviewOutcome;
import dev.beacon.domain.interviews.InterviewType;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Same fields as {@link ScheduleInterviewRequest}, plus the version the edit started from. */
public record UpdateInterviewRequest(
        @NotNull LocalDateTime localStart,
        @NotBlank @Size(max = 64) String timeZone,
        @NotNull @Min(5) @Max(720) Integer durationMinutes,
        @NotNull InterviewType type,
        @Nullable @Size(max = 2048) String location,
        @Nullable List<UUID> interviewerIds,
        @Nullable @Size(max = 10000) String prepNotes,
        @Nullable @Size(max = 10000) String debriefNotes,
        @Nullable InterviewOutcome outcome,
        @NotNull Long version
) implements InterviewFields {
}
