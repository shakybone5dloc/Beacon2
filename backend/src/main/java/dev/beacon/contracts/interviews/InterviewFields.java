package dev.beacon.contracts.interviews;

import dev.beacon.domain.interviews.InterviewOutcome;
import dev.beacon.domain.interviews.InterviewType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** What schedule and update interview requests share; the records' accessors implement it. */
public interface InterviewFields {
    LocalDateTime localStart();
    String timeZone();
    Integer durationMinutes();
    InterviewType type();
    String location();
    List<UUID> interviewerIds();
    String prepNotes();
    String debriefNotes();
    InterviewOutcome outcome();
}
