package dev.beacon.infrastructure.persistence;

import dev.beacon.domain.contacts.Contact;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The outreach log. Callers check ownership of the person or application first,
 * or use the finder that takes the owner.
 */
public interface ContactRepository extends JpaRepository<Contact, UUID> {

    @EntityGraph(attributePaths = {"person", "application", "application.company"})
    Optional<Contact> findByIdAndOwnerId(UUID id, UUID ownerId);

    /** A person's log, newest first, with what each contact was about. */
    @EntityGraph(attributePaths = {"person", "application", "application.company"})
    List<Contact> findAllByPersonIdOrderByOccurredAtDesc(UUID personId);

    /** Contacts about one application, newest first, with who they were with. */
    @EntityGraph(attributePaths = {"person", "application", "application.company"})
    List<Contact> findAllByApplicationIdOrderByOccurredAtDesc(UUID applicationId);

    /** Removes a person's log, through JPA so loaded contacts don't go stale. */
    void deleteAllByPersonId(UUID personId);
}
