package dev.beacon.domain.users;

import jakarta.persistence.*;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** A code that lets one person sign up while Beacon is invite-only. */
@Entity
@Table(name = "invite_codes")
public class InviteCode {

    /** No 0/O, 1/I/L or U, so a code read aloud or typed from a phone comes out right. */
    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private String code;

    private String note;

    @Column(updatable = false)
    private UUID createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false, updatable = false)
    private Instant expiresAt;

    private UUID usedBy;

    private Instant usedAt;

    private Instant revokedAt;

    protected InviteCode() { }

    public static InviteCode create(UUID createdBy, String note, Instant now, Instant expiresAt) {
        var invite = new InviteCode();
        invite.id = UUID.randomUUID();
        invite.code = newCode();
        invite.note = note == null || note.isBlank() ? null : note.strip();
        invite.createdBy = createdBy;
        invite.createdAt = now;
        invite.expiresAt = expiresAt;
        return invite;
    }

    /** Twelve characters in three groups, about 59 bits: not guessable at the allowed sign-up rate. */
    static String newCode() {
        var code = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            if (i > 0 && i % 4 == 0) code.append('-');
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /** How a typed code is compared: case, spaces and dashes don't matter. */
    public static String normalize(String typed) {
        if (typed == null) return "";
        var letters = typed.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (letters.length() != 12) return letters;
        return letters.substring(0, 4) + "-" + letters.substring(4, 8) + "-" + letters.substring(8);
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && revokedAt == null && now.isBefore(expiresAt);
    }

    public void revoke(Instant now) {
        if (revokedAt == null && usedAt == null) revokedAt = now;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getNote() { return note; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public UUID getUsedBy() { return usedBy; }
    public Instant getUsedAt() { return usedAt; }
    public Instant getRevokedAt() { return revokedAt; }
}
