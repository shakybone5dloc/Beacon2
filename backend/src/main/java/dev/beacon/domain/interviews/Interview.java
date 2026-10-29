package dev.beacon.domain.interviews;

import dev.beacon.domain.applications.JobApplication;
import dev.beacon.domain.people.Person;
import jakarta.persistence.*;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** One interview on an application, owned by the application's owner. */
@Entity
@Table(name = "interviews")
public class Interview {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", updatable = false)
    private JobApplication application;

    @Column(nullable = false)
    private Instant startsAt;

    /** IANA zone id, e.g. "America/Chicago". */
    @Column(nullable = false, length = 64)
    private String timeZone;

    @Column(nullable = false)
    private int durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InterviewType type;

    @Column(length = 2048)
    private String location;

    @Column(length = 10000)
    private String prepNotes;

    @Column(length = 10000)
    private String debriefNotes;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private InterviewOutcome outcome;

    /**
     * A plain many-to-many: the join table has no columns of its own, unlike
     * application_people, which needs a join entity for its role.
     */
    @ManyToMany
    @JoinTable(name = "interview_people",
            joinColumns = @JoinColumn(name = "interview_id"),
            inverseJoinColumns = @JoinColumn(name = "person_id"))
    @OrderBy("name")
    private Set<Person> interviewers = new LinkedHashSet<>();

    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Interview() { }

    public static Interview schedule(JobApplication application, Details details, Instant now) {
        var interview = new Interview();
        interview.id = UUID.randomUUID();
        interview.ownerId = application.getOwnerId();
        interview.application = application;
        interview.createdAt = now;
        interview.update(details, now);
        return interview;
    }

    public void update(Details details, Instant now) {
        if (details.localStart() == null || details.timeZone() == null) {
            throw new IllegalArgumentException("An interview needs a start time and time zone");
        }
        if (details.durationMinutes() <= 0) throw new IllegalArgumentException("An interview needs a duration");
        if (details.type() == null) throw new IllegalArgumentException("An interview needs a type");
        for (var person : details.interviewers()) {
            if (!person.getOwnerId().equals(ownerId)) {
                throw new IllegalArgumentException("An interviewer belongs to another user");
            }
        }
        this.startsAt = details.localStart().atZone(details.timeZone()).toInstant();
        this.timeZone = details.timeZone().getId();
        this.durationMinutes = details.durationMinutes();
        this.type = details.type();
        this.location = trimToNull(details.location());
        this.prepNotes = trimToNull(details.prepNotes());
        this.debriefNotes = trimToNull(details.debriefNotes());
        this.outcome = details.outcome();
        this.interviewers.clear();
        this.interviewers.addAll(details.interviewers());
        this.updatedAt = now;
    }

    /** The person is being deleted. */
    public void removeInterviewer(Person person) {
        interviewers.remove(person);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /**
     * Everything about an interview that can be edited. The start is a wall-clock
     * time in {@code timeZone}, the way it was agreed ("2pm Central").
     */
    public record Details(LocalDateTime localStart, ZoneId timeZone, int durationMinutes, InterviewType type,
                          String location, Collection<Person> interviewers, String prepNotes,
                          String debriefNotes, InterviewOutcome outcome) { }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public JobApplication getApplication() { return application; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return startsAt.plus(Duration.ofMinutes(durationMinutes)); }
    public String getTimeZone() { return timeZone; }
    /** The start as a wall-clock time in its own zone. */
    public LocalDateTime getLocalStart() { return LocalDateTime.ofInstant(startsAt, ZoneId.of(timeZone)); }
    public int getDurationMinutes() { return durationMinutes; }
    public InterviewType getType() { return type; }
    public String getLocation() { return location; }
    public String getPrepNotes() { return prepNotes; }
    public String getDebriefNotes() { return debriefNotes; }
    public InterviewOutcome getOutcome() { return outcome; }
    public Set<Person> getInterviewers() { return Collections.unmodifiableSet(interviewers); }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
