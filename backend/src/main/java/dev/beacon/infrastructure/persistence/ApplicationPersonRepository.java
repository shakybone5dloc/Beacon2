package dev.beacon.infrastructure.persistence;

import dev.beacon.domain.people.ApplicationPerson;
import dev.beacon.domain.people.PersonRole;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Links between applications and people. Callers check ownership of the
 * application or person first; a link always joins two rows of the same owner.
 */
public interface ApplicationPersonRepository extends JpaRepository<ApplicationPerson, ApplicationPerson.Key> {

    /** The people on one application, with their companies, in one query. */
    @EntityGraph(attributePaths = {"person", "person.company"})
    List<ApplicationPerson> findAllByApplicationIdOrderByAddedAtAsc(UUID applicationId);

    /** Removes a person from every application, through JPA so loaded links don't go stale. */
    void deleteAllByPersonId(UUID personId);

    /** Removes everyone from an application, through JPA for the same reason. */
    void deleteAllByApplicationId(UUID applicationId);

    /** One person's applications, with each application's company. */
    @EntityGraph(attributePaths = {"application", "application.company"})
    List<ApplicationPerson> findAllByPersonIdOrderByAddedAtDesc(UUID personId);

    /**
     * For each of these applications, the companies of the people linked with this
     * role: [application id, company name]. Used to show "via <agency>" on undisclosed roles.
     */
    @Query("""
            select ap.application.id, c.name from ApplicationPerson ap
            join ap.person p join p.company c
            where ap.application.id in :applicationIds and ap.role = :role
            order by ap.addedAt
            """)
    List<Object[]> findCompanyNamesByRole(Collection<UUID> applicationIds, PersonRole role);
}
