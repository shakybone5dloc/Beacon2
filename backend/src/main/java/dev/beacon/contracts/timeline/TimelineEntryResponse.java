package dev.beacon.contracts.timeline;

import dev.beacon.contracts.applications.StatusChangeResponse;
import dev.beacon.contracts.contacts.ContactResponse;
import dev.beacon.contracts.documents.DocumentResponse;
import dev.beacon.contracts.interviews.InterviewResponse;
import dev.beacon.contracts.people.ApplicationPersonResponse;
import jakarta.annotation.Nullable;

import java.time.Instant;

/**
 * One event in an application's timeline. Exactly one of the detail fields is set:
 * the one that {@code kind} names. Each reuses the response its own endpoint returns.
 *
 * @param at when it happened; for an interview, when it starts (which may be ahead)
 */
public record TimelineEntryResponse(
        TimelineKind kind,
        Instant at,
        @Nullable StatusChangeResponse statusChange,
        @Nullable ContactResponse contact,
        @Nullable InterviewResponse interview,
        @Nullable DocumentResponse document,
        @Nullable ApplicationPersonResponse person
) {
    public enum TimelineKind {
        STATUS_CHANGE,
        CONTACT,
        INTERVIEW,
        DOCUMENT,
        /** Someone was added to the application. */
        PERSON_ADDED
    }

    public static TimelineEntryResponse of(StatusChangeResponse c) {
        return new TimelineEntryResponse(TimelineKind.STATUS_CHANGE, c.changedAt(), c, null, null, null, null);
    }

    public static TimelineEntryResponse of(ContactResponse c) {
        return new TimelineEntryResponse(TimelineKind.CONTACT, c.occurredAt(), null, c, null, null, null);
    }

    public static TimelineEntryResponse of(InterviewResponse i) {
        return new TimelineEntryResponse(TimelineKind.INTERVIEW, i.startsAt(), null, null, i, null, null);
    }

    public static TimelineEntryResponse of(DocumentResponse d) {
        return new TimelineEntryResponse(TimelineKind.DOCUMENT, d.createdAt(), null, null, null, d, null);
    }

    public static TimelineEntryResponse of(ApplicationPersonResponse p) {
        return new TimelineEntryResponse(TimelineKind.PERSON_ADDED, p.addedAt(), null, null, null, null, p);
    }
}
