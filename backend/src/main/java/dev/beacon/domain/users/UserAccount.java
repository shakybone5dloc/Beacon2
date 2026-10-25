package dev.beacon.domain.users;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** Someone who can sign in to Beacon. Named to avoid clashing with Spring Security's {@code User}. */
@Entity
@Table(name = "users")
public class UserAccount {

    @Id
    private UUID id;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(nullable = false, length = 200)
    private String passwordHash;

    @Column(length = 100)
    private String displayName;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected UserAccount() { }

    /** @param passwordHash already encoded; the domain never sees a plain password */
    public static UserAccount register(String email, String passwordHash, String displayName, Instant now) {
        if (email == null || email.isBlank() || passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("Email and password hash are required");
        }
        var user = new UserAccount();
        user.id = UUID.randomUUID();
        user.email = normalizeEmail(email);
        user.passwordHash = passwordHash;
        user.displayName = displayName == null || displayName.isBlank() ? null : displayName.strip();
        user.createdAt = now;
        return user;
    }

    /** Emails compare case-insensitively, so they are stored lower-cased. */
    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public Instant getCreatedAt() { return createdAt; }
}
