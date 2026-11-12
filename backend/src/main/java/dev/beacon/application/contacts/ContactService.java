package dev.beacon.application.contacts;

import dev.beacon.contracts.contacts.ContactFields;
import dev.beacon.contracts.contacts.ContactResponse;
import dev.beacon.contracts.contacts.CreateContactRequest;
import dev.beacon.contracts.contacts.UpdateContactRequest;
import dev.beacon.domain.applications.JobApplication;
import dev.beacon.domain.contacts.Contact;
import dev.beacon.domain.people.ApplicationPerson;
import dev.beacon.domain.people.Person;
import dev.beacon.infrastructure.persistence.ApplicationPersonRepository;
import dev.beacon.infrastructure.persistence.ContactRepository;
import dev.beacon.infrastructure.persistence.JobApplicationRepository;
import dev.beacon.infrastructure.persistence.PersonRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The outreach log. Contacts belong to one user; every method takes the owner's id,
 * and another user's contacts, people and applications behave as if they don't exist.
 * Create and update throw {@link UnknownReferenceException} for a person or
 * application id that isn't the owner's, and for a contact that names both a person and a
 * team, neither, or a team without an application.
 */
@Service
public class ContactService {

    private final ContactRepository contacts;
    private final PersonRepository people;
    private final JobApplicationRepository applications;
    private final ApplicationPersonRepository links;
    private final Clock clock;

    public ContactService(ContactRepository contacts, PersonRepository people, JobApplicationRepository applications,
                          ApplicationPersonRepository links, Clock clock) {
        this.contacts = contacts;
        this.people = people;
        this.applications = applications;
        this.links = links;
        this.clock = clock;
    }

    /** Empty when the person does not exist. */
    @Transactional(readOnly = true)
    public Optional<List<ContactResponse>> withPerson(UUID ownerId, UUID personId) {
        return people.findByIdAndOwnerId(personId, ownerId).map(person ->
                contacts.findAllByPersonIdOrderByOccurredAtDesc(person.getId()).stream()
                        .map(ContactService::toResponse)
                        .toList());
    }

    /** Empty when the application does not exist. */
    @Transactional(readOnly = true)
    public Optional<List<ContactResponse>> aboutApplication(UUID ownerId, UUID applicationId) {
        if (!applications.existsByIdAndOwnerId(applicationId, ownerId)) return Optional.empty();
        return Optional.of(contacts.findAllByApplicationIdOrderByOccurredAtDesc(applicationId).stream()
                .map(ContactService::toResponse)
                .toList());
    }

    @Transactional
    public ContactResponse create(UUID ownerId, CreateContactRequest request) {
        var now = Instant.now(clock);
        var contact = Contact.create(ownerId, details(ownerId, request, now), now);
        contacts.saveAndFlush(contact);
        return toResponse(contact);
    }

    @Transactional
    public Optional<ContactResponse> update(UUID ownerId, UUID id, UpdateContactRequest request) {
        var found = contacts.findByIdAndOwnerId(id, ownerId);
        if (found.isEmpty()) return Optional.empty();
        var contact = found.get();
        if (contact.getVersion().longValue() != request.version()) {
            throw new OptimisticLockingFailureException(String.format(
                    "Contact with id %s has version %d, which does not match version %d", id, contact.getVersion(), request.version()));
        }
        var now = Instant.now(clock);
        contact.update(details(ownerId, request, now), now);
        contacts.flush();
        return Optional.of(toResponse(contact));
    }

    @Transactional
    public boolean delete(UUID ownerId, UUID id) {
        var found = contacts.findByIdAndOwnerId(id, ownerId);
        found.ifPresent(contacts::delete);
        return found.isPresent();
    }

    /** Looks up the person and application, and puts the person on that application if they aren't yet. */
    private Contact.Details details(UUID ownerId, ContactFields f, Instant now) {
        var team = f.team() == null || f.team().isBlank() ? null : f.team().strip();
        if (f.personId() == null && team == null) {
            throw new UnknownReferenceException("personId", "Pick who it was with: a person or a team");
        }
        if (f.personId() != null && team != null) {
            throw new UnknownReferenceException("team", "Pick a person or a team, not both");
        }
        if (team != null && f.applicationId() == null) {
            throw new UnknownReferenceException("applicationId", "A contact with a team needs its application");
        }
        var person = f.personId() == null ? null : people.findByIdAndOwnerId(f.personId(), ownerId)
                .orElseThrow(() -> new UnknownReferenceException("personId", "No such person"));
        JobApplication application = null;
        if (f.applicationId() != null) {
            application = applications.findByIdAndOwnerId(f.applicationId(), ownerId)
                    .orElseThrow(() -> new UnknownReferenceException("applicationId", "No such application"));
            if (person != null) linkIfNeeded(application, person, now);
        }
        return new Contact.Details(person, team, application, f.channel(), f.direction(), f.occurredAt(), f.body());
    }

    /** Someone who contacted the user about an application is involved in it, in their usual role. */
    private void linkIfNeeded(JobApplication application, Person person, Instant now) {
        var key = new ApplicationPerson.Key(application.getId(), person.getId());
        if (!links.existsById(key)) links.save(ApplicationPerson.link(application, person, null, now));
    }

    public static ContactResponse toResponse(Contact c) {
        var app = c.getApplication();
        var person = c.getPerson();
        return new ContactResponse(c.getId(), person == null ? null : person.getId(),
                person == null ? null : person.getName(), c.getTeam(),
                app == null ? null : app.getId(), app == null ? null : app.getCompanyName(),
                app == null ? null : app.getRole(), c.getChannel(), c.getDirection(), c.getOccurredAt(), c.getBody(),
                c.getVersion(), c.getCreatedAt(), c.getUpdatedAt());
    }

    /** A person or application id in a request that isn't one of the owner's. */
    public static class UnknownReferenceException extends RuntimeException {
        private final String field;

        public UnknownReferenceException(String field, String message) {
            super(message);
            this.field = field;
        }

        public String field() { return field; }
    }
}
