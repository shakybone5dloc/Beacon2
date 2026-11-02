package dev.beacon.infrastructure.postings;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
        store(key, value, now);
        return value;
    }

    /**
     * Loads outside the lock, so several keys can load at once (careers-site detail
     * calls run in parallel). Two callers asking for the same missing key may both load it.
     */
    V getConcurrently(K key, Supplier<V> loader) {
        return peek(key).orElseGet(() -> {
            var value = loader.get();
            synchronized (this) {
                store(key, value, clock.instant());
            }
            return value;
        });
    }

    private synchronized Optional<V> peek(K key) {
        var cached = entries.get(key);
        return cached != null && cached.expiresAt().isAfter(clock.instant()) ? Optional.of(cached.value()) : Optional.empty();
    }

    private void store(K key, V value, Instant now) {
        entries.values().removeIf(e -> !e.expiresAt().isAfter(now));
        if (entries.size() >= maxEntries) {
            entries.clear();
        }
        entries.put(key, new Entry<>(value, now.plus(ttl)));
    }
}
