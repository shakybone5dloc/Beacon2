package dev.beacon.application.users;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ends sign-ins that started before a user's password changed (L5): the security filter chain
 * asks {@link #isRevoked} on each request and drops such a session. Kept in memory, like the
 * sessions themselves, so a restart forgets both; remember-me cookies are signed with the
 * password hash, so a new password already cancels those.
 */
@Component
public class SessionRevocations {

    /** The session attribute holding when the session signed in. */
    public static final String SIGNED_IN_AT = "beacon.signedInAt";

    private final Map<UUID, Instant> changedAt = new ConcurrentHashMap<>();

    /** Every session of this user that signed in before {@code at} stops working. */
    public void revokeBefore(UUID userId, Instant at) {
        changedAt.merge(userId, at, (a, b) -> a.isAfter(b) ? a : b);
    }

    public boolean isRevoked(UUID userId, Instant signedInAt) {
        var at = changedAt.get(userId);
        return at != null && signedInAt.isBefore(at);
    }
}
