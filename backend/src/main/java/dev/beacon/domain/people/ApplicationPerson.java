package dev.beacon.domain.people;

import dev.beacon.domain.applications.JobApplication;
import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * A person's part in one application. A join entity rather than a plain
 * many-to-many, because the link has data of its own: the role and when it was added.
 */
@Entity
@Table(name = "application_people")
public class ApplicationPerson {

    @EmbeddedId
    private Key id;

    @MapsId("applicationId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id")
    private JobApplication application;

    @MapsId("personId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id")
    private Person person;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PersonRole role;

    @Column(nullable = false, updatable = false)
    private Instant addedAt;

    protected ApplicationPerson() { }

    public static ApplicationPerson link(JobApplication application, Person person, PersonRole role, Instant now) {
        if (!application.getOwnerId().equals(person.getOwnerId())) {
            throw new IllegalArgumentException("The person belongs to another user");
        }
        var link = new ApplicationPerson();
        link.id = new Key(application.getId(), person.getId());
        link.application = application;
        link.person = person;
        link.role = role == null ? person.getRole() : role;
        link.addedAt = now;
        return link;
    }

    public void changeRole(PersonRole role) {
        this.role = role;
    }

    public JobApplication getApplication() { return application; }
    public Person getPerson() { return person; }
    public PersonRole getRole() { return role; }
    public Instant getAddedAt() { return addedAt; }

    @Embeddable
    public record Key(UUID applicationId, UUID personId) implements Serializable { }
}
