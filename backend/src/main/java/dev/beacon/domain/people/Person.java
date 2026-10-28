package dev.beacon.domain.people;

import dev.beacon.domain.companies.Company;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** Someone the user deals with in their job search, owned by that user. */
@Entity
@Table(name = "people")
public class Person {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 200)
    private String name;

    /** Where they work; null when unknown. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PersonRole role;

    /** Only set for recruiters. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private RecruiterType recruiterType;

    @Column(length = 320)
    private String email;

    @Column(length = 50)
    private String phone;

    @Column(length = 2048)
    private String linkedinUrl;

    @Column(length = 4000)
    private String notes;

    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Person() { }

    public static Person create(UUID ownerId, Details details, Instant now) {
        if (ownerId == null) throw new IllegalArgumentException("A person needs an owner");
        var person = new Person();
        person.id = UUID.randomUUID();
        person.ownerId = ownerId;
        person.createdAt = now;
        person.update(details, now);
        return person;
    }

    public void update(Details details, Instant now) {
        if (details.name() == null || details.name().isBlank()) throw new IllegalArgumentException("A person needs a name");
        if (details.role() == null) throw new IllegalArgumentException("A person needs a role");
        if (details.company() != null && !details.company().getOwnerId().equals(ownerId)) {
            throw new IllegalArgumentException("The company belongs to another user");
        }
        this.name = details.name().strip();
        this.company = details.company();
        this.title = trimToNull(details.title());
        this.role = details.role();
        // A recruiter type means nothing for anyone else, so it's dropped when the role changes.
        this.recruiterType = details.role() == PersonRole.RECRUITER ? details.recruiterType() : null;
        this.email = trimToNull(details.email());
        this.phone = trimToNull(details.phone());
        this.linkedinUrl = trimToNull(details.linkedinUrl());
        this.notes = trimToNull(details.notes());
        this.updatedAt = now;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** Everything about a person that can be edited. */
    public record Details(String name, Company company, String title, PersonRole role, RecruiterType recruiterType,
                          String email, String phone, String linkedinUrl, String notes) { }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public Company getCompany() { return company; }
    public String getTitle() { return title; }
    public PersonRole getRole() { return role; }
    public RecruiterType getRecruiterType() { return recruiterType; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getLinkedinUrl() { return linkedinUrl; }
    public String getNotes() { return notes; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
