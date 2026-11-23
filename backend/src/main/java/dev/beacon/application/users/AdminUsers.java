package dev.beacon.application.users;

import dev.beacon.contracts.auth.AdminUserResponse;
import dev.beacon.infrastructure.persistence.UserAccountRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The admin's view of every account: who signed up, when they last used Beacon, and switching an
 * account off and on. Switching off ends its sessions straight away; its data stays as it is.
 */
@Service
public class AdminUsers {

    /** Thrown for a change an admin can't make here, like switching off an admin. */
    public static class NotAllowedException extends RuntimeException {
        public NotAllowedException(String message) {
            super(message);
        }
    }

    private final JdbcTemplate jdbc;
    private final UserAccountRepository users;
    private final Admins admins;
    private final SessionRevocations revocations;
    private final Clock clock;

    public AdminUsers(JdbcTemplate jdbc, UserAccountRepository users, Admins admins, SessionRevocations revocations,
                      Clock clock) {
        this.jdbc = jdbc;
        this.users = users;
        this.admins = admins;
        this.revocations = revocations;
        this.clock = clock;
    }

    /** Newest accounts first. */
    @Transactional(readOnly = true)
    public List<AdminUserResponse> list() {
        return jdbc.query("""
                select u.id, u.email, u.display_name, u.created_at, u.email_confirmed_at, u.last_active_at,
                       u.disabled_at,
                       (select count(*) from job_applications a where a.owner_id = u.id) as applications
                from users u
                order by u.created_at desc
                """, (rs, i) -> {
            var email = rs.getString("email");
            return new AdminUserResponse(
                    rs.getObject("id", UUID.class),
                    email,
                    rs.getString("display_name"),
                    instant(rs.getTimestamp("created_at")),
                    rs.getTimestamp("email_confirmed_at") != null,
                    instant(rs.getTimestamp("last_active_at")),
                    instant(rs.getTimestamp("disabled_at")),
                    admins.isAdmin(email),
                    rs.getInt("applications"));
        });
    }

    /** Switches the account off and ends its sessions. Admins can't be switched off here. */
    @Transactional
    public Optional<AdminUserResponse> disable(UUID id) {
        var found = users.findById(id);
        if (found.isEmpty()) return Optional.empty();
        var user = found.get();
        if (admins.isAdmin(user.getEmail())) {
            throw new NotAllowedException("Admins can't be switched off here; take them out of BEACON_ADMIN_EMAILS first.");
        }
        user.disable(Instant.now(clock));
        users.flush();
        revocations.revokeBefore(id, Instant.now());
        return find(id);
    }

    /** Switches the account back on; it signs in again as usual. */
    @Transactional
    public Optional<AdminUserResponse> enable(UUID id) {
        var found = users.findById(id);
        if (found.isEmpty()) return Optional.empty();
        found.get().enable();
        users.flush();
        return find(id);
    }

    private Optional<AdminUserResponse> find(UUID id) {
        return list().stream().filter(u -> u.id().equals(id)).findFirst();
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
