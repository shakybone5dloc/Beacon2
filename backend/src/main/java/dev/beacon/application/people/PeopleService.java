package dev.beacon.application.people;

import dev.beacon.application.companies.CompanyService;
import dev.beacon.contracts.people.ApplicationPersonResponse;
import dev.beacon.contracts.people.CreatePersonRequest;
import dev.beacon.contracts.people.PersonApplicationResponse;
import dev.beacon.contracts.people.PersonFields;
import dev.beacon.contracts.people.PersonResponse;
import dev.beacon.contracts.people.UpdatePersonRequest;
import dev.beacon.domain.companies.CompanyType;
import dev.beacon.domain.people.ApplicationPerson;
import dev.beacon.domain.people.Person;
import dev.beacon.domain.people.PersonRole;
import dev.beacon.domain.people.RecruiterType;
import dev.beacon.infrastructure.persistence.ApplicationPersonRepository;
import dev.beacon.infrastructure.persistence.CompanyRepository;
import dev.beacon.infrastructure.persistence.ContactRepository;
import dev.beacon.infrastructure.persistence.InterviewRepository;
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
 * People belong to one user, and so do their links to applications. Every method
 * takes the owner's id; another user's people and applications behave as if they
 * don't exist. Methods that take a company throw {@link CompanyService.UnknownCompanyException}
 * for a company id that isn't the owner's.
 */
@Service
public class PeopleService {

    private final PersonRepository people;
    private final ApplicationPersonRepository links;
    private final ContactRepository contacts;
    private final InterviewRepository interviews;
    private final JobApplicationRepository applications;
    private final CompanyRepository companies;
    private final CompanyService companyService;
    private final Clock clock;

    public PeopleService(PersonRepository people, ApplicationPersonRepository links, ContactRepository contacts,
                         InterviewRepository interviews, JobApplicationRepository applications,
                         CompanyRepository companies, CompanyService companyService, Clock clock) {
        this.people = people;
        this.links = links;
        this.contacts = contacts;
        this.interviews = interviews;
        this.applications = applications;
        this.companies = companies;
        this.companyService = companyService;
        this.clock = clock;
    }

    /** Everyone by name, or up to ten whose name contains {@code query}. */
    @Transactional(readOnly = true)
    public List<PersonResponse> list(UUID ownerId, String query) {
        var found = query == null || query.isBlank()
                ? people.findAllByOwnerIdOrderByNameAsc(ownerId)
                : people.findTop10ByOwnerIdAndNameContainingIgnoreCaseOrderByNameAsc(ownerId, query.strip());
        return found.stream().map(PeopleService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Optional<PersonResponse> get(UUID ownerId, UUID id) {
        return people.findByIdAndOwnerId(id, ownerId).map(PeopleService::toResponse);
    }

    @Transactional
    public PersonResponse create(UUID ownerId, CreatePersonRequest request) {
        var now = Instant.now(clock);
        var person = Person.create(ownerId, details(ownerId, request, now), now);
        people.save(person);
        return toResponse(person);
    }

    @Transactional
    public Optional<PersonResponse> update(UUID ownerId, UUID id, UpdatePersonRequest request) {
        var found = people.findByIdAndOwnerId(id, ownerId);
        if (found.isEmpty()) return Optional.empty();
        var person = found.get();
        if (person.getVersion().longValue() != request.version()) {
            throw new OptimisticLockingFailureException(String.format(
                    "Person with id %s has version %d, which does not match version %d", id, person.getVersion(), request.version()));
        }
        var now = Instant.now(clock);
        person.update(details(ownerId, request, now), now);
        people.flush();
        return Optional.of(toResponse(person));
    }

    /**
     * Also removes them from every application and interview they were on, and
     * deletes their contact log. The interviews themselves stay.
     */
    @Transactional
    public boolean delete(UUID ownerId, UUID id) {
        var found = people.findByIdAndOwnerId(id, ownerId);
        found.ifPresent(person -> {
            // The database would cascade too, but links already loaded in this
            // transaction would then point at a deleted person.
            links.deleteAllByPersonId(person.getId());
            contacts.deleteAllByPersonId(person.getId());
            interviews.findAllByInterviewersId(person.getId()).forEach(i -> i.removeInterviewer(person));
            people.delete(person);
        });
        return found.isPresent();
    }

    /** Empty when the person does not exist. */
    @Transactional(readOnly = true)
    public Optional<List<PersonApplicationResponse>> applicationsOf(UUID ownerId, UUID personId) {
        return people.findByIdAndOwnerId(personId, ownerId).map(person ->
                links.findAllByPersonIdOrderByAddedAtDesc(person.getId()).stream()
                        .map(link -> {
                            var app = link.getApplication();
                            return new PersonApplicationResponse(app.getId(), app.getCompanyName(), app.getRole(),
                                    app.getStatus(), link.getRole(), link.getAddedAt());
                        })
                        .toList());
    }

    /** Empty when the company does not exist. */
    @Transactional(readOnly = true)
    public Optional<List<PersonResponse>> atCompany(UUID ownerId, UUID companyId) {
        return companies.findByIdAndOwnerId(companyId, ownerId).map(company ->
                people.findAllByOwnerIdAndCompanyIdOrderByNameAsc(ownerId, company.getId()).stream()
                        .map(PeopleService::toResponse)
                        .toList());
    }

    /** Empty when the application does not exist. */
    @Transactional(readOnly = true)
    public Optional<List<ApplicationPersonResponse>> onApplication(UUID ownerId, UUID applicationId) {
        if (!applications.existsByIdAndOwnerId(applicationId, ownerId)) return Optional.empty();
        return Optional.of(links.findAllByApplicationIdOrderByAddedAtAsc(applicationId).stream()
                .map(PeopleService::toResponse)
                .toList());
    }

    /**
     * Puts a person on an application, or changes their role there if they already
     * are. Empty when either doesn't exist. A null role uses the person's usual one.
     */
    @Transactional
    public Optional<ApplicationPersonResponse> link(UUID ownerId, UUID applicationId, UUID personId, PersonRole role) {
        var application = applications.findByIdAndOwnerId(applicationId, ownerId);
        var person = people.findByIdAndOwnerId(personId, ownerId);
        if (application.isEmpty() || person.isEmpty()) return Optional.empty();

        var key = new ApplicationPerson.Key(applicationId, personId);
        var link = links.findById(key).map(existing -> {
            existing.changeRole(role == null ? existing.getRole() : role);
            return existing;
        }).orElseGet(() -> links.save(ApplicationPerson.link(application.get(), person.get(), role, Instant.now(clock))));
        links.flush();
        return Optional.of(toResponse(link));
    }

    /** False when the application, the person or the link doesn't exist. */
    @Transactional
    public boolean unlink(UUID ownerId, UUID applicationId, UUID personId) {
        if (!applications.existsByIdAndOwnerId(applicationId, ownerId)) return false;
        var link = links.findById(new ApplicationPerson.Key(applicationId, personId));
        link.ifPresent(links::delete);
        return link.isPresent();
    }

    private Person.Details details(UUID ownerId, PersonFields f, Instant now) {
        var company = companyService.resolve(ownerId, f, typeOfNewCompany(f), now);
        return new Person.Details(f.name(), company, f.title(), f.role(), f.recruiterType(),
                f.email(), f.phone(), f.linkedinUrl(), f.notes());
    }

    /** A company first named by an agency or RPO recruiter is their agency, not an employer. */
    private static CompanyType typeOfNewCompany(PersonFields f) {
        var agencyRecruiter = f.role() == PersonRole.RECRUITER
                && (f.recruiterType() == RecruiterType.AGENCY || f.recruiterType() == RecruiterType.RPO);
        return agencyRecruiter ? CompanyType.AGENCY : CompanyType.EMPLOYER;
    }

    static PersonResponse toResponse(Person p) {
        var company = p.getCompany();
        return new PersonResponse(p.getId(), p.getName(), company == null ? null : company.getId(),
                company == null ? null : company.getName(), p.getTitle(), p.getRole(), p.getRecruiterType(),
                p.getEmail(), p.getPhone(), p.getLinkedinUrl(), p.getNotes(), p.getVersion(), p.getCreatedAt(), p.getUpdatedAt());
    }

    private static ApplicationPersonResponse toResponse(ApplicationPerson link) {
        return new ApplicationPersonResponse(toResponse(link.getPerson()), link.getRole(), link.getAddedAt());
    }
}
