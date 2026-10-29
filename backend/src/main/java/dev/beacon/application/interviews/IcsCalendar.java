package dev.beacon.application.interviews;

import dev.beacon.application.documents.Filenames;
import dev.beacon.domain.interviews.Interview;
import dev.beacon.domain.interviews.InterviewType;
import dev.beacon.domain.people.Person;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Writes one interview as an iCalendar (RFC 5545) file that any calendar app can import.
 * Times are in UTC, so no time-zone definitions are needed; the calendar app shows them
 * in the viewer's zone. The UID stays the same and SEQUENCE follows the version, so
 * importing an edited interview again updates the event instead of adding a second one.
 */
public final class IcsCalendar {

    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    /** Lines longer than this many bytes are folded. */
    private static final int MAX_LINE_BYTES = 75;

    private IcsCalendar() { }

    public static String of(Interview interview, Instant now) {
        var app = interview.getApplication();
        var lines = new ArrayList<String>();
        lines.add("BEGIN:VCALENDAR");
        lines.add("VERSION:2.0");
        lines.add("PRODID:-//Beacon//Interviews//EN");
        lines.add("CALSCALE:GREGORIAN");
        lines.add("METHOD:PUBLISH");
        lines.add("BEGIN:VEVENT");
        lines.add("UID:" + interview.getId() + "@beacon");
        lines.add("SEQUENCE:" + interview.getVersion());
        lines.add("DTSTAMP:" + UTC.format(now));
        lines.add("DTSTART:" + UTC.format(interview.getStartsAt()));
        lines.add("DTEND:" + UTC.format(interview.getEndsAt()));
        lines.add("SUMMARY:" + text(label(interview.getType()) + ": " + app.getEmployerLabel() + " – " + app.getRole()));
        if (interview.getLocation() != null) {
            lines.add("LOCATION:" + text(interview.getLocation()));
            if (interview.getLocation().matches("(?i)https?://\\S+")) lines.add("URL:" + interview.getLocation());
        }
        var description = description(interview);
        if (!description.isEmpty()) lines.add("DESCRIPTION:" + text(description));
        lines.add("END:VEVENT");
        lines.add("END:VCALENDAR");
        return lines.stream().map(IcsCalendar::fold).collect(Collectors.joining("\r\n", "", "\r\n"));
    }

    /** "interview-acme-2026-10-30.ics". */
    public static String fileName(Interview interview) {
        var day = interview.getLocalStart().toLocalDate();
        return "interview-" + Filenames.slug(interview.getApplication().getEmployerLabel()) + "-" + day + ".ics";
    }

    private static String description(Interview interview) {
        var parts = new ArrayList<String>();
        if (!interview.getInterviewers().isEmpty()) {
            parts.add("Interviewers: " + interview.getInterviewers().stream().map(IcsCalendar::who)
                    .collect(Collectors.joining(", ")));
        }
        if (interview.getPrepNotes() != null) parts.add("Prep notes:\n" + interview.getPrepNotes());
        return String.join("\n\n", parts);
    }

    private static String who(Person person) {
        return person.getTitle() == null ? person.getName() : person.getName() + " (" + person.getTitle() + ")";
    }

    static String label(InterviewType type) {
        return switch (type) {
            case PHONE_SCREEN -> "Phone screen";
            case TECHNICAL -> "Technical interview";
            case PANEL -> "Panel interview";
            case ONSITE -> "Onsite interview";
            case OTHER -> "Interview";
        };
    }

    /** Escapes a TEXT value: backslashes, semicolons, commas and line breaks. */
    static String text(String value) {
        return value.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\n")
                .replace("\n", "\\n");
    }

    /** Splits a line into 75-byte pieces, each continuation starting with a space, never inside a character. */
    static String fold(String line) {
        if (line.getBytes(StandardCharsets.UTF_8).length <= MAX_LINE_BYTES) return line;
        List<String> pieces = new ArrayList<>();
        var current = new StringBuilder();
        var bytes = 0;
        var limit = MAX_LINE_BYTES;
        for (var i = 0; i < line.length(); ) {
            var codePoint = line.codePointAt(i);
            var chars = Character.charCount(codePoint);
            var size = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
            if (bytes + size > limit) {
                pieces.add(current.toString());
                current.setLength(0);
                bytes = 0;
                limit = MAX_LINE_BYTES - 1; // continuation lines start with a space
            }
            current.appendCodePoint(codePoint);
            bytes += size;
            i += chars;
        }
        pieces.add(current.toString());
        return String.join("\r\n ", pieces);
    }
}
