package dev.beacon.application.interviews;

import dev.beacon.contracts.interviews.InterviewFields;
import dev.beacon.contracts.interviews.InterviewResponse;
import dev.beacon.contracts.interviews.ScheduleInterviewRequest;
import dev.beacon.contracts.interviews.UpdateInterviewRequest;
import dev.beacon.domain.applications.JobApplication;
import dev.beacon.domain.interviews.Interview;
import dev.beacon.domain.people.ApplicationPerson;
import dev.beacon.domain.people.Person;
import dev.beacon.infrastructure.persistence.ApplicationPersonRepository;
import dev.beacon.infrastructure.persistence.InterviewRepository;
import dev.beacon.infrastructure.persistence.JobApplicationRepository;
import dev.beacon.infrastructure.persistence.PersonRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Interviews belong to one user. Every method takes the owner's id, and another
 * user's interviews, applications and people behave as if they don't exist.
 * Schedule and update throw {@link InvalidFieldException} for an unknown time zone
 * or an interviewer who isn't one of the owner's people.
 */
@Service
public class InterviewService {

    private final InterviewRepository interviews;
    private final JobApplicationRepository applications;
    private final PersonRepository people;
    private final ApplicationPersonRepository links;
    private final Clock clock;

    public InterviewService(InterviewRepository interviews, JobApplicationRepository applications,
                            PersonRepository people, ApplicationPersonRepository links, Clock clock) {
        this.interviews = interviews;
        this.applications = applications;
        this.people = people;
        this.links = links;
        this.clock = clock;
    }

    /** Earliest first. Empty when the application does not exist. */
    @Transactional(readOnly = true)
    public Optional<List<InterviewResponse>> onApplication(UUID ownerId, UUID applicationId) {
        if (!applications.existsByIdAndOwnerId(applicationId, ownerId)) return Optional.empty();
        return Optional.of(interviews.findAllByApplicationIdOrderByStartsAtAsc(applicationId).stream()
                .map(InterviewService::toResponse)
                .toList());
    }

    /** Empty when the application does not exist. */
    @Transactional
    public Optional<InterviewResponse> schedule(UUID ownerId, UUID applicationId, ScheduleInterviewRequest request) {
        var found = applications.findByIdAndOwnerId(applicationId, ownerId);
        if (found.isEmpty()) return Optional.empty();
        var application = found.get();
        var now = Instant.now(clock);
        var interview = Interview.schedule(application, details(application, request, now), now);
        interviews.saveAndFlush(interview);
        return Optional.of(toResponse(interview));
    }

    @Transactional
    public Optional<InterviewResponse> update(UUID ownerId, UUID id, UpdateInterviewRequest request) {
        var found = interviews.findByIdAndOwnerId(id, ownerId);
        if (found.isEmpty()) return Optional.empty();
        var interview = found.get();
        if (interview.getVersion().longValue() != request.version()) {
            throw new OptimisticLockingFailureException(String.format(
                    "Interview with id %s has version %d, which does not match version %d", id, interview.getVersion(), request.version()));
        }
        var now = Instant.now(clock);
        interview.update(details(interview.getApplication(), request, now), now);
        interviews.flush();
        return Optional.of(toResponse(interview));
    }

    @Transactional
    public boolean delete(UUID ownerId, UUID id) {
        var found = interviews.findByIdAndOwnerId(id, ownerId);
        found.ifPresent(interviews::delete);
        return found.isPresent();
    }

    /** The interview as an .ics file: [file name, content]. Empty when it does not exist. */
    @Transactional(readOnly = true)
    public Optional<CalendarFile> calendar(UUID ownerId, UUID id) {
        return interviews.findByIdAndOwnerId(id, ownerId).map(interview ->
                new CalendarFile(IcsCalendar.fileName(interview), IcsCalendar.of(interview, Instant.now(clock))));
    }

    private Interview.Details details(JobApplication application, InterviewFields f, Instant now) {
        ZoneId zone;
        try {
            zone = ZoneId.of(f.timeZone());
        } catch (DateTimeException ex) {
            throw new InvalidFieldException("timeZone", "Unknown time zone");
        }
        var interviewers = new ArrayList<Person>();
        for (var personId : new LinkedHashSet<>(f.interviewerIds() == null ? List.of() : f.interviewerIds())) {
            var person = people.findByIdAndOwnerId(personId, application.getOwnerId())
                    .orElseThrow(() -> new InvalidFieldException("interviewerIds", "No such person"));
            linkIfNeeded(application, person, now);
            interviewers.add(person);
        }
        return new Interview.Details(f.localStart(), zone, f.durationMinutes(), f.type(), f.location(),
                interviewers, f.prepNotes(), f.debriefNotes(), f.outcome());
    }

    /** An interviewer is involved in the application, in their usual role. */
    private void linkIfNeeded(JobApplication application, Person person, Instant now) {
        var key = new ApplicationPerson.Key(application.getId(), person.getId());
        if (!links.existsById(key)) links.save(ApplicationPerson.link(application, person, null, now));
    }

    private static InterviewResponse toResponse(Interview i) {
        var interviewers = i.getInterviewers().stream()
                .map(p -> new InterviewResponse.InterviewerResponse(p.getId(), p.getName(),
                        p.getCompany() == null ? null : p.getCompany().getName()))
                .toList();
        return new InterviewResponse(i.getId(), i.getApplication().getId(), i.getStartsAt(), i.getEndsAt(),
                i.getLocalStart(), i.getTimeZone(), i.getDurationMinutes(), i.getType(), i.getLocation(), interviewers,
                i.getPrepNotes(), i.getDebriefNotes(), i.getOutcome(), i.getVersion(), i.getCreatedAt(), i.getUpdatedAt());
    }

    public record CalendarFile(String fileName, String content) { }

    /** A request field that names something that doesn't exist, such as a time zone or a person. */
    public static class InvalidFieldException extends RuntimeException {
        private final String field;

        public InvalidFieldException(String field, String message) {
            super(message);
            this.field = field;
        }

        public String field() { return field; }
    }
}
