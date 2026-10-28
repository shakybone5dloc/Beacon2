package dev.beacon.application.applications;

import dev.beacon.contracts.applications.PossibleDuplicateResponse;
import dev.beacon.domain.applications.JobApplication;
import dev.beacon.domain.applications.JobTitles;
import dev.beacon.domain.people.ApplicationPerson;
import dev.beacon.domain.people.PersonRole;
import dev.beacon.infrastructure.persistence.ApplicationPersonRepository;
import dev.beacon.infrastructure.persistence.JobApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Spots a second recruiter pitching what looks like the same job: another of the
 * user's applications at the same company, with the same title, added within
 * {@link #WINDOW} of this one. An undisclosed employer can't be matched.
 */
@Service
public class DuplicatePitchService {

    /** "Within a few weeks": long enough to cover a slow second pitch. */
    static final Duration WINDOW = Duration.ofDays(42);

    private final JobApplicationRepository applications;
    private final ApplicationPersonRepository links;

    public DuplicatePitchService(JobApplicationRepository applications, ApplicationPersonRepository links) {
        this.applications = applications;
        this.links = links;
    }

    /** Empty when the application does not exist. */
    @Transactional(readOnly = true)
    public Optional<List<PossibleDuplicateResponse>> find(UUID ownerId, UUID applicationId) {
        return applications.findByIdAndOwnerId(applicationId, ownerId).map(this::duplicatesOf);
    }

    private List<PossibleDuplicateResponse> duplicatesOf(JobApplication application) {
        if (application.getCompany() == null) return List.of();
        var title = JobTitles.normalize(application.getRole());
        var matches = applications
                .findAllByOwnerIdAndCompanyIdOrderByCreatedAtDesc(application.getOwnerId(), application.getCompany().getId())
                .stream()
                .filter(other -> !other.getId().equals(application.getId()))
                .filter(other -> JobTitles.normalize(other.getRole()).equals(title))
                .filter(other -> Duration.between(other.getCreatedAt(), application.getCreatedAt()).abs().compareTo(WINDOW) <= 0)
                .toList();
        if (matches.isEmpty()) return List.of();

        var recruiters = links.findAllByApplicationIdInAndRoleOrderByAddedAtAsc(
                        matches.stream().map(JobApplication::getId).toList(), PersonRole.RECRUITER)
                .stream()
                .collect(Collectors.groupingBy(link -> link.getApplication().getId(),
                        Collectors.mapping(DuplicatePitchService::recruiterLabel, Collectors.toList())));
        return matches.stream()
                .map(other -> new PossibleDuplicateResponse(other.getId(), other.getCompanyName(), other.getRole(),
                        other.getStatus(), other.getCreatedAt(), recruiters.getOrDefault(other.getId(), List.of())))
                .toList();
    }

    private static String recruiterLabel(ApplicationPerson link) {
        var person = link.getPerson();
        return person.getCompany() == null ? person.getName() : person.getName() + " (" + person.getCompany().getName() + ")";
    }
}
