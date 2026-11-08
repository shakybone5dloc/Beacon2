package dev.beacon.application.digest;

import dev.beacon.contracts.digest.DigestPreviewResponse;
import dev.beacon.contracts.digest.DigestSettingsResponse;
import dev.beacon.contracts.digest.SaveDigestSettingsRequest;
import dev.beacon.domain.digest.DigestSettings;
import dev.beacon.domain.users.UserAccount;
import dev.beacon.infrastructure.persistence.DigestSettingsRepository;
import dev.beacon.infrastructure.persistence.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

/**
 * The weekly digest (A5): Home's sections emailed on the day and hour each user picks.
 * Users can preview it and send one to themselves at any time.
 */
@Service
public class DigestService {

    private static final Logger log = LoggerFactory.getLogger(DigestService.class);

    private final DigestSettingsRepository settings;
    private final UserAccountRepository users;
    private final DigestBuilder builder;
    private final Mailer mailer;
    private final TransactionTemplate transactions;
    private final String appUrl;
    private final Clock clock;

    public DigestService(DigestSettingsRepository settings, UserAccountRepository users, DigestBuilder builder,
                         Mailer mailer, TransactionTemplate transactions,
                         @Value("${beacon.app-url:http://localhost:4200}") String appUrl, Clock clock) {
        this.settings = settings;
        this.users = users;
        this.builder = builder;
        this.mailer = mailer;
        this.transactions = transactions;
        this.appUrl = appUrl;
        this.clock = clock;
    }

    /** The user's settings; defaults (off, Monday 8 AM in {@code zone}) until they save some. */
    public DigestSettingsResponse get(UUID userId, ZoneId zone) {
        return transactions.execute(tx -> toResponse(settings.findById(userId)
                .orElseGet(() -> DigestSettings.defaults(userId, zone, Instant.now(clock))), user(userId)));
    }

    /** A stale version is a conflict, as everywhere else. */
    public DigestSettingsResponse save(UUID userId, SaveDigestSettingsRequest request, DayOfWeek day, ZoneId zone) {
        return transactions.execute(tx -> {
            var now = Instant.now(clock);
            var existing = settings.findById(userId);
            if (existing.isPresent() && !Objects.equals(existing.get().getVersion(), request.version())) {
                throw new OptimisticLockingFailureException("The digest settings were changed somewhere else.");
            }
            var s = existing.orElseGet(() -> DigestSettings.defaults(userId, zone, now));
            s.change(request.enabled(), day, request.hour(), zone, now);
            return toResponse(settings.saveAndFlush(s), user(userId));
        });
    }

    /** The email as it would go out right now, without sending it. */
    public DigestPreviewResponse preview(UUID userId, ZoneId zone) {
        var email = render(userId, zone);
        return new DigestPreviewResponse(email.subject(), email.html(), email.text());
    }

    /** Sends the digest now, to the user's own address; the weekly schedule is unchanged. */
    public void sendNow(UUID userId, ZoneId zone) {
        if (!mailer.isConfigured()) {
            throw new DigestException("Email isn't set up on this Beacon server yet, so nothing can be sent.", false);
        }
        var email = render(userId, zone);
        mailer.send(user(userId).getEmail(), email.subject(), email.html(), email.text());
    }

    /**
     * Sends every digest whose time has come, one user at a time so one failure doesn't stop
     * the rest. Called by the scheduler; returns how many went out.
     */
    public int sendDue() {
        if (!mailer.isConfigured()) return 0;
        var now = Instant.now(clock);
        var due = transactions.execute(tx -> settings.findAllByEnabledTrue().stream()
                .filter(s -> s.isDue(now))
                .map(DigestSettings::getUserId)
                .toList());
        int sent = 0;
        for (var userId : Objects.requireNonNull(due)) {
            try {
                var s = transactions.execute(tx -> settings.findById(userId).orElseThrow());
                sendNow(userId, Objects.requireNonNull(s).zoneId());
                transactions.executeWithoutResult(tx -> settings.findById(userId).ifPresent(found -> found.sent(now)));
                sent++;
            } catch (RuntimeException ex) {
                log.warn("Weekly digest for user {} wasn't sent: {}", userId, ex.getMessage());
            }
        }
        return sent;
    }

    private DigestEmail.Rendered render(UUID userId, ZoneId zone) {
        var digest = transactions.execute(tx -> builder.build(userId, user(userId).getDisplayName(), zone, Instant.now(clock)));
        return DigestEmail.render(Objects.requireNonNull(digest), appUrl);
    }

    private UserAccount user(UUID userId) {
        return users.findById(userId).orElseThrow();
    }

    private DigestSettingsResponse toResponse(DigestSettings s, UserAccount user) {
        var next = s.nextSendAt(Instant.now(clock));
        return new DigestSettingsResponse(s.isEnabled(), s.getDayOfWeek().name(), s.getHour(), s.zoneId().getId(),
                user.getEmail(), s.getLastSentAt(), next == null ? null : next.toOffsetDateTime(),
                mailer.isConfigured(), s.getVersion() == null ? 0 : s.getVersion());
    }
}
