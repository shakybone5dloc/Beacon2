package dev.beacon.application.users;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limits how often sign-in and sign-up can be tried (L2), so passwords and invite codes can't be
 * guessed by brute force and one address can't flood the server with password checks. Kept in
 * memory: fine for one server; limits start over when it restarts.
 *
 * <ul>
 *   <li>Sign-in, per address: {@code beacon.limits.login-per-ip} attempts per 15 minutes.</li>
 *   <li>Sign-in, per account: {@code beacon.limits.login-failures-per-account} wrong passwords
 *       per 15 minutes; after that the account can't sign in until the oldest ages out. A
 *       successful sign-in clears its count.</li>
 *   <li>Sign-up, per address: {@code beacon.limits.register-per-ip} attempts per hour.</li>
 *   <li>Password-reset requests, per address: {@code beacon.limits.email-requests-per-ip} per hour.</li>
 *   <li>Emails sent to one account (reset and confirmation links):
 *       {@code beacon.limits.emails-per-account} per hour, so no one can flood an inbox.</li>
 * </ul>
 */
@Component
public class AttemptLimiter {

    public enum Kind {
        LOGIN_IP(Duration.ofMinutes(15)),
        LOGIN_ACCOUNT(Duration.ofMinutes(15)),
        REGISTER_IP(Duration.ofHours(1)),
        EMAIL_REQUEST_IP(Duration.ofHours(1)),
        EMAIL_ACCOUNT(Duration.ofHours(1));

        final Duration window;

        Kind(Duration window) {
            this.window = window;
        }
    }

    /** Past this many keys, expired ones are swept out, so the map can't grow without bound. */
    private static final int SWEEP_AT = 10_000;

    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();
    private final Map<Kind, Integer> limits;
    private final Clock clock;

    public AttemptLimiter(@Value("${beacon.limits.login-per-ip:30}") int loginPerIp,
                          @Value("${beacon.limits.login-failures-per-account:8}") int failuresPerAccount,
                          @Value("${beacon.limits.register-per-ip:10}") int registerPerIp,
                          @Value("${beacon.limits.email-requests-per-ip:10}") int emailRequestsPerIp,
                          @Value("${beacon.limits.emails-per-account:3}") int emailsPerAccount,
                          Clock clock) {
        this.limits = Map.of(Kind.LOGIN_IP, loginPerIp, Kind.LOGIN_ACCOUNT, failuresPerAccount,
                Kind.REGISTER_IP, registerPerIp, Kind.EMAIL_REQUEST_IP, emailRequestsPerIp,
                Kind.EMAIL_ACCOUNT, emailsPerAccount);
        this.clock = clock;
    }

    /** How long until another attempt is allowed; empty when one is allowed now. */
    public Optional<Duration> blockedFor(Kind kind, String key) {
        var now = Instant.now(clock);
        var times = attempts.get(id(kind, key));
        if (times == null) return Optional.empty();
        synchronized (times) {
            prune(times, kind, now);
            if (times.size() < limits.get(kind)) return Optional.empty();
            return Optional.of(Duration.between(now, times.peekFirst().plus(kind.window)));
        }
    }

    public void record(Kind kind, String key) {
        var now = Instant.now(clock);
        if (attempts.size() > SWEEP_AT) sweep(now);
        var times = attempts.computeIfAbsent(id(kind, key), k -> new ArrayDeque<>());
        synchronized (times) {
            prune(times, kind, now);
            times.addLast(now);
        }
    }

    public void clear(Kind kind, String key) {
        attempts.remove(id(kind, key));
    }

    private static String id(Kind kind, String key) {
        return kind.name() + ":" + key;
    }

    private static void prune(Deque<Instant> times, Kind kind, Instant now) {
        var cutoff = now.minus(kind.window);
        while (!times.isEmpty() && !times.peekFirst().isAfter(cutoff)) times.removeFirst();
    }

    private void sweep(Instant now) {
        attempts.entrySet().removeIf(entry -> {
            var kind = Kind.valueOf(entry.getKey().substring(0, entry.getKey().indexOf(':')));
            synchronized (entry.getValue()) {
                prune(entry.getValue(), kind, now);
                return entry.getValue().isEmpty();
            }
        });
    }
}
