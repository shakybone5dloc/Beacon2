package dev.beacon.application.timeline;

import dev.beacon.application.applications.ApplicationMappings;
import dev.beacon.application.contacts.ContactService;
import dev.beacon.application.documents.DocumentService;
import dev.beacon.application.interviews.InterviewService;
import dev.beacon.application.people.PeopleService;
import dev.beacon.contracts.timeline.TimelineEntryResponse;
import dev.beacon.infrastructure.persistence.ApplicationPersonRepository;
import dev.beacon.infrastructure.persistence.ContactRepository;
import dev.beacon.infrastructure.persistence.DocumentRepository;
import dev.beacon.infrastructure.persistence.InterviewRepository;
import dev.beacon.infrastructure.persistence.JobApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything that happened on one application, newest first: status changes, people
 * added, contacts, interviews and documents. A read model with no table of its own:
 * one small query per source, merged in memory. An application has tens of events,
 * not thousands, so there is nothing to page.
 */
@Service
public class TimelineService {

    /** Newest first; for events at the same instant, the order the sources are added below. */
    private static final Comparator<TimelineEntryResponse> NEWEST_FIRST =
            Comparator.comparing(TimelineEntryResponse::at).reversed();

    private final JobApplicationRepository applications;
    private final ApplicationPersonRepository links;
    private final ContactRepository contacts;
    private final InterviewRepository interviews;
    private final DocumentRepository documents;

    public TimelineService(JobApplicationRepository applications, ApplicationPersonRepository links,
                           ContactRepository contacts, InterviewRepository interviews, DocumentRepository documents) {
        this.applications = applications;
        this.links = links;
        this.contacts = contacts;
        this.interviews = interviews;
        this.documents = documents;
    }

    /** Empty when the application does not exist. */
    @Transactional(readOnly = true)
    public Optional<List<TimelineEntryResponse>> of(UUID ownerId, UUID applicationId) {
        return applications.findByIdAndOwnerId(applicationId, ownerId).map(application -> {
            var entries = new ArrayList<TimelineEntryResponse>();
            application.getStatusHistory().stream()
                    .map(ApplicationMappings::toResponse)
                    .map(TimelineEntryResponse::of)
                    .forEach(entries::add);
            links.findAllByApplicationIdOrderByAddedAtAsc(applicationId).stream()
                    .map(PeopleService::toResponse)
                    .map(TimelineEntryResponse::of)
                    .forEach(entries::add);
            contacts.findAllByApplicationIdOrderByOccurredAtDesc(applicationId).stream()
                    .map(ContactService::toResponse)
                    .map(TimelineEntryResponse::of)
                    .forEach(entries::add);
            interviews.findAllByApplicationIdOrderByStartsAtAsc(applicationId).stream()
                    .map(InterviewService::toResponse)
                    .map(TimelineEntryResponse::of)
                    .forEach(entries::add);
            documents.findInfoByApplication(ownerId, applicationId).stream()
                    .map(DocumentService::toResponse)
                    .map(TimelineEntryResponse::of)
                    .forEach(entries::add);
            entries.sort(NEWEST_FIRST);
            return entries;
        });
    }
}
