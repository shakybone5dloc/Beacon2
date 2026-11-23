package dev.beacon.application.users;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps {@code users.last_active_at} to within an hour, for the admin's user list: the first
 * signed-in request of each hour writes it, the rest only check a map in memory.
 */
@Component
public class ActivityTracker {

    static final Duration EVERY = Duration.ofHours(1);

    private final Map<UUID, Instant> written = new ConcurrentHashMap<>();
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ActivityTracker(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public void seen(UUID userId) {
        var now = Instant.now(clock);
        var last = written.get(userId);
        if (last != null && last.plus(EVERY).isAfter(now)) return;
        written.put(userId, now);
        jdbc.update("update users set last_active_at = ? where id = ?", Timestamp.from(now), userId);
    }
}
