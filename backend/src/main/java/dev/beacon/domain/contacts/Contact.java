package dev.beacon.domain.contacts;

import dev.beacon.domain.applications.JobApplication;
import dev.beacon.domain.people.Person;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** One message, text or call with a person, optionally about one application. */
@Entity
@Table(name = "contacts")
public class Contact {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    /** Who it was with; null when it was with a team instead. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    private Person person;

    /**
     * The team it was with when there's no person to name, e.g. "Hiring team" for an email to
     * a recruiting inbox. Such a contact is always about an application, whose company says
     * whose team it is.
     */
    @Column(length = 100)
    private String team;

    /** What it was about; null for general outreach, or once the application is deleted. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    private JobApplication application;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContactChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ContactDirection direction;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(length = 20000)
    private String body;

    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Contact() { }

    public static Contact create(UUID ownerId, Details details, Instant now) {
        if (ownerId == null) throw new IllegalArgumentException("A contact needs an owner");
        var contact = new Contact();
        contact.id = UUID.randomUUID();
        contact.ownerId = ownerId;
        contact.createdAt = now;
        contact.update(details, now);
        return contact;
    }

    public void update(Details details, Instant now) {
        var team = details.team() == null || details.team().isBlank() ? null : details.team().strip();
        if ((details.person() == null) == (team == null)) {
            throw new IllegalArgumentException("A contact is with a person or a team, one of the two");
        }
        if (team != null && details.application() == null) {
            throw new IllegalArgumentException("A contact with a team needs its application");
        }
        if (details.channel() == null) throw new IllegalArgumentException("A contact needs a channel");
        if (details.direction() == null) throw new IllegalArgumentException("A contact needs a direction");
        if (details.occurredAt() == null) throw new IllegalArgumentException("A contact needs a date");
        if (details.person() != null && !details.person().getOwnerId().equals(ownerId)) {
            throw new IllegalArgumentException("The person belongs to another user");
        }
        if (details.application() != null && !details.application().getOwnerId().equals(ownerId)) {
            throw new IllegalArgumentException("The application belongs to another user");
        }
        this.person = details.person();
        this.team = team;
        this.application = details.application();
        this.channel = details.channel();
        this.direction = details.direction();
        this.occurredAt = details.occurredAt();
        this.body = details.body() == null || details.body().isBlank() ? null : details.body().strip();
        this.updatedAt = now;
    }

    /** The application is being deleted; the contact stays in the person's log. */
    public void forgetApplication() {
        this.application = null;
    }

    /** Everything about a contact that can be edited: {@code person} or {@code team}, never both. */
    public record Details(Person person, String team, JobApplication application, ContactChannel channel,
                          ContactDirection direction, Instant occurredAt, String body) { }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public Person getPerson() { return person; }
    public String getTeam() { return team; }
    public JobApplication getApplication() { return application; }
    public ContactChannel getChannel() { return channel; }
    public ContactDirection getDirection() { return direction; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getBody() { return body; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
