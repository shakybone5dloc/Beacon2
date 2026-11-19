package dev.beacon.domain.users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * A one-time link emailed to a user: to reset their password or to confirm their email. The link
 * carries a random token; only its SHA-256 hash is stored, so the database alone can't be used to
 * follow one.
 */
@Entity
@Table(name = "account_links")
public class AccountLink {

    public enum Purpose { RESET_PASSWORD, CONFIRM_EMAIL }

    private static final SecureRandom RANDOM = new SecureRandom();

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Purpose purpose;

    @Column(nullable = false, updatable = false)
    private String tokenHash;

    @Column(nullable = false, updatable = false)
    private String email;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false, updatable = false)
    private Instant expiresAt;

    private Instant usedAt;

    protected AccountLink() { }

    /** A new link and the token for its URL; the token isn't kept anywhere else. */
    public record Issued(AccountLink link, String token) {}

    public static Issued issue(UUID userId, Purpose purpose, String email, Instant now, Instant expiresAt) {
        var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        var token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var link = new AccountLink();
        link.id = UUID.randomUUID();
        link.userId = userId;
        link.purpose = purpose;
        link.tokenHash = hash(token);
        link.email = email;
        link.createdAt = now;
        link.expiresAt = expiresAt;
        return new Issued(link, token);
    }

    public static String hash(String token) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Unused and not expired. */
    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public void use(Instant now) {
        usedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public Purpose getPurpose() { return purpose; }
    public String getEmail() { return email; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
}
