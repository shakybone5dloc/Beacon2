package dev.beacon.infrastructure.postings;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Keeps job source responses for a while so repeated searches stay inside each
 * source's rate limits. Failures are not cached.
 */
final class TtlCache<K, V> {

    private record Entry<V>(V value, Instant expiresAt) {}

    private final Duration ttl;
    private final Clock clock;
    private final int maxEntries;
    private final Map<K, Entry<V>> entries = new HashMap<>();

    TtlCache(Duration ttl, Clock clock, int maxEntries) {
        this.ttl = ttl;
        this.clock = clock;
        this.maxEntries = maxEntries;
    }

    synchronized V get(K key, Supplier<V> loader) {
        var now = clock.instant();
        var cached = entries.get(key);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.value();
        }

        var value = loader.get();
        entries.values().removeIf(e -> !e.expiresAt().isAfter(now));
        if (entries.size() >= maxEntries) {
            entries.clear();
        }
        entries.put(key, new Entry<>(value, now.plus(ttl)));
        return value;
    }
}
