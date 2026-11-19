package dev.beacon.application.users;

import dev.beacon.application.digest.Mailer;
import dev.beacon.domain.users.AccountLink;
import dev.beacon.domain.users.AccountLink.Purpose;
import dev.beacon.domain.users.UserAccount;
import dev.beacon.infrastructure.persistence.AccountLinkRepository;
import dev.beacon.infrastructure.persistence.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Password reset and email confirmation (L5): one-time links emailed to the user.
 *
 * <ul>
 *   <li>A reset link works once, within {@link #RESET_FOR}. Asking for one answers the same
 *       whether or not the address has an account, and the email is sent in the background so
 *       the answer takes the same time either way. Setting the new password ends every other
 *       sign-in and confirms the email, since the link proved the inbox is theirs.</li>
 *   <li>A confirmation link is sent at sign-up and works for {@link #CONFIRM_FOR}.</li>
 *   <li>Only the newest link of each kind works, and an account gets at most
 *       {@code beacon.limits.emails-per-account} of these emails an hour.</li>
 * </ul>
 */
@Service
public class AccountLinks {

    static final Duration RESET_FOR = Duration.ofHours(1);
    static final Duration CONFIRM_FOR = Duration.ofDays(7);

    private static final Logger log = LoggerFactory.getLogger(AccountLinks.class);

    /** Thrown when a link is unknown, expired, already used or no longer matches the account. */
    public static class InvalidLinkException extends RuntimeException {
        public InvalidLinkException(String message) {
            super(message);
        }
    }

    private final UserAccountRepository users;
    private final AccountLinkRepository links;
    private final Mailer mailer;
    private final PasswordEncoder passwordEncoder;
    private final AttemptLimiter limiter;
    private final SessionRevocations revocations;
    private final Clock clock;
    private final String appUrl;
    private final ExecutorService background;

    public AccountLinks(UserAccountRepository users, AccountLinkRepository links, Mailer mailer,
                        PasswordEncoder passwordEncoder, AttemptLimiter limiter, SessionRevocations revocations,
                        Clock clock, @Value("${beacon.app-url:http://localhost:4200}") String appUrl,
                        @Value("${beacon.mail.send-in-background:true}") boolean sendInBackground) {
        this.users = users;
        this.links = links;
        this.mailer = mailer;
        this.passwordEncoder = passwordEncoder;
        this.limiter = limiter;
        this.revocations = revocations;
        this.clock = clock;
        this.appUrl = appUrl.endsWith("/") ? appUrl.substring(0, appUrl.length() - 1) : appUrl;
        this.background = sendInBackground ? Executors.newVirtualThreadPerTaskExecutor() : null;
    }

    /** Whether this server can send these emails at all. */
    public boolean canEmail() {
        return mailer.isConfigured();
    }

    /**
     * Emails a reset link if the address has an account and hasn't had too many emails this
     * hour. Says nothing either way, so it can't be used to find out who has an account.
     */
    public void requestPasswordReset(String email) {
        if (!mailer.isConfigured()) return;
        var normalized = UserAccount.normalizeEmail(email);
        users.findByEmail(normalized).ifPresent(user -> {
            if (limiter.blockedFor(AttemptLimiter.Kind.EMAIL_ACCOUNT, user.getEmail()).isPresent()) return;
            limiter.record(AttemptLimiter.Kind.EMAIL_ACCOUNT, user.getEmail());
            var token = issue(user, Purpose.RESET_PASSWORD, RESET_FOR);
            var mail = AccountEmails.passwordReset(link("/reset-password", token));
            send(user.getEmail(), mail);
        });
    }

    /**
     * Sets a new password from a reset link. Ends every sign-in that started before now, clears
     * a sign-in lockout and confirms the email.
     */
    @Transactional
    public void resetPassword(String token, String password) {
        var now = Instant.now(clock);
        var link = redeem(token, Purpose.RESET_PASSWORD, now);
        var user = users.findById(link.getUserId())
                .orElseThrow(() -> new InvalidLinkException(expired()));
        user.changePassword(passwordEncoder.encode(password));
        if (user.getEmail().equals(link.getEmail())) user.confirmEmail(now);
        links.deleteAllForUser(user.getId(), Purpose.RESET_PASSWORD);
        revocations.revokeBefore(user.getId(), Instant.now());
        limiter.clear(AttemptLimiter.Kind.LOGIN_ACCOUNT, user.getEmail());
    }

    /**
     * Emails a confirmation link, unless the email is already confirmed or the server can't
     * send mail. Throws {@link TooManyAttemptsException} past the hourly limit.
     */
    public void sendConfirmation(UUID userId) {
        if (!mailer.isConfigured()) return;
        var user = users.findById(userId).orElseThrow();
        if (user.isEmailConfirmed()) return;
        limiter.blockedFor(AttemptLimiter.Kind.EMAIL_ACCOUNT, user.getEmail()).ifPresent(wait -> {
            throw new TooManyAttemptsException(wait);
        });
        limiter.record(AttemptLimiter.Kind.EMAIL_ACCOUNT, user.getEmail());
        var token = issue(user, Purpose.CONFIRM_EMAIL, CONFIRM_FOR);
        send(user.getEmail(), AccountEmails.confirmEmail(link("/confirm-email", token)));
    }

    /** Confirms the email the link was sent to, if it's still the account's email. */
    @Transactional
    public void confirmEmail(String token) {
        var now = Instant.now(clock);
        var link = redeem(token, Purpose.CONFIRM_EMAIL, now);
        var user = users.findById(link.getUserId())
                .filter(u -> u.getEmail().equals(link.getEmail()))
                .orElseThrow(() -> new InvalidLinkException(expired()));
        user.confirmEmail(now);
    }

    @Transactional(readOnly = true)
    public boolean isEmailConfirmed(UUID userId) {
        return users.findById(userId).map(UserAccount::isEmailConfirmed).orElse(false);
    }

    private String issue(UserAccount user, Purpose purpose, Duration validFor) {
        var now = Instant.now(clock);
        links.deleteAllForUser(user.getId(), purpose);
        var issued = AccountLink.issue(user.getId(), purpose, user.getEmail(), now, now.plus(validFor));
        links.save(issued.link());
        return issued.token();
    }

    /** Marks the link used in one statement, so it works exactly once; then loads it. */
    private AccountLink redeem(String token, Purpose purpose, Instant now) {
        if (token == null || token.isBlank()) throw new InvalidLinkException(expired());
        var hash = AccountLink.hash(token.strip());
        if (links.use(hash, purpose, now) == 0) throw new InvalidLinkException(expired());
        return links.findByTokenHashAndPurpose(hash, purpose).orElseThrow(() -> new InvalidLinkException(expired()));
    }

    private static String expired() {
        return "This link has expired or was already used. Ask for a new one.";
    }

    private String link(String path, String token) {
        return appUrl + path + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private void send(String to, AccountEmails.Email mail) {
        Runnable task = () -> {
            try {
                mailer.send(to, mail.subject(), mail.html(), mail.text());
            } catch (RuntimeException ex) {
                log.warn("Couldn't send \"{}\": {}", mail.subject(), ex.getMessage());
            }
        };
        if (background != null) background.execute(task);
        else task.run();
    }
}
