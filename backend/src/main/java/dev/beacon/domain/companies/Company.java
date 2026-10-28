package dev.beacon.domain.companies;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * An employer or agency, owned by one user. Applications link to it, so a company
 * is one row instead of free-text names that drift apart ("Acme" vs "ACME, Inc.").
 */
@Entity
@Table(name = "companies")
public class Company {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 200)
    private String name;

    /** {@link CompanyNames#normalize}; unique per owner. */
    @Column(nullable = false, length = 200)
    private String normalizedName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CompanyType type;

    @Column(length = 2048)
    private String website;

    /** Domains the company's email comes from, e.g. "acme.com"; matched against incoming email later. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> emailDomains = new ArrayList<>();

    @Column(length = 4000)
    private String notes;

    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Company() { }

    public static Company create(UUID ownerId, String name, Instant now) {
        return create(ownerId, name, CompanyType.EMPLOYER, now);
    }

    public static Company create(UUID ownerId, String name, CompanyType type, Instant now) {
        if (ownerId == null) throw new IllegalArgumentException("A company needs an owner");
        var company = new Company();
        company.id = UUID.randomUUID();
        company.ownerId = ownerId;
        company.rename(name);
        company.type = type == null ? CompanyType.EMPLOYER : type;
        company.createdAt = now;
        company.updatedAt = now;
        return company;
    }

    public void update(String name, CompanyType type, String website, List<String> emailDomains, String notes, Instant now) {
        rename(name);
        this.type = type == null ? CompanyType.EMPLOYER : type;
        this.website = trimToNull(website);
        this.emailDomains = emailDomains == null ? new ArrayList<>() : emailDomains.stream()
                .map(d -> d.strip().toLowerCase(Locale.ROOT))
                .filter(d -> !d.isEmpty())
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
        this.notes = trimToNull(notes);
        this.updatedAt = now;
    }

    private void rename(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A company needs a name");
        this.name = name.strip();
        this.normalizedName = CompanyNames.normalize(name);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public String getNormalizedName() { return normalizedName; }
    public CompanyType getType() { return type; }
    public String getWebsite() { return website; }
    public List<String> getEmailDomains() { return List.copyOf(emailDomains); }
    public String getNotes() { return notes; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
