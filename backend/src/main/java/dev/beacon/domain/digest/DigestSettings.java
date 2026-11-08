package dev.beacon.domain.digest;

import jakarta.persistence.*;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * When a user's weekly digest (A5) goes out: a day, a local hour and their time zone.
 * Off until they turn it on.
 */
@Entity
@Table(name = "digest_settings")
public class DigestSettings {

    @Id
    private UUID userId;

    @Column(nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @Column(nullable = false)
    private int hour;

    @Column(nullable = false, length = 64)
    private String zone;

    private Instant lastSentAt;

    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected DigestSettings() {}

    /** Off, Mondays at 8 in the morning in the given zone, until the user changes it. */
    public static DigestSettings defaults(UUID userId, ZoneId zone, Instant now) {
        var s = new DigestSettings();
        s.userId = userId;
        s.enabled = false;
        s.dayOfWeek = DayOfWeek.MONDAY;
        s.hour = 8;
        s.zone = zone.getId();
        s.createdAt = now;
        s.updatedAt = now;
        return s;
    }

    public void change(boolean enabled, DayOfWeek dayOfWeek, int hour, ZoneId zone, Instant now) {
        this.enabled = enabled;
        this.dayOfWeek = dayOfWeek;
        this.hour = hour;
        this.zone = zone.getId();
        this.updatedAt = now;
    }

    /**
     * True when this week's send time has passed and it hasn't gone out since. A digest
     * missed by more than a day (the server was down) waits for next week.
     */
    public boolean isDue(Instant now) {
        if (!enabled) return false;
        var local = now.atZone(zoneId());
        if (local.getDayOfWeek() != dayOfWeek || local.getHour() < hour) return false;
        var slot = local.toLocalDate().atTime(hour, 0).atZone(zoneId()).toInstant();
        return lastSentAt == null || lastSentAt.isBefore(slot);
    }

    public void sent(Instant now) {
        this.lastSentAt = now;
    }

    /** The next time it goes out, for the settings page; null while it's off. */
    public ZonedDateTime nextSendAt(Instant now) {
        if (!enabled) return null;
        var today = now.atZone(zoneId()).toLocalDate();
        for (int i = 0; ; i++) {
            var at = today.plusDays(i).atTime(hour, 0).atZone(zoneId());
            if (at.getDayOfWeek() == dayOfWeek && at.toInstant().isAfter(now)) return at;
        }
    }

    public ZoneId zoneId() { return ZoneId.of(zone); }
    public UUID getUserId() { return userId; }
    public boolean isEnabled() { return enabled; }
    public DayOfWeek getDayOfWeek() { return dayOfWeek; }
    public int getHour() { return hour; }
    public Instant getLastSentAt() { return lastSentAt; }
    public Long getVersion() { return version; }
}
