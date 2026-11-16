package dev.beacon.application.users;

import dev.beacon.domain.users.UserAccount;
import dev.beacon.infrastructure.persistence.UserAccountRepository;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/** Registers accounts and loads them for sign-in. */
@Service
public class AccountService implements UserDetailsService {

    private final UserAccountRepository users;
    private final InviteService invites;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AccountService(UserAccountRepository users, InviteService invites, PasswordEncoder passwordEncoder,
                          Clock clock) {
        this.users = users;
        this.invites = invites;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /** Thrown when an account with the email already exists. */
    public static class EmailTakenException extends RuntimeException {
        public EmailTakenException() {
            super("An account with this email already exists");
        }
    }

    @Transactional
    public SignedInUser register(String email, String password, String displayName) {
        var normalized = UserAccount.normalizeEmail(email);
        if (users.existsByEmail(normalized)) {
            throw new EmailTakenException();
        }
        var account = UserAccount.register(normalized, passwordEncoder.encode(password), displayName, Instant.now(clock));
        users.save(account);
        return SignedInUser.of(account);
    }

    /**
     * Signs someone up with an invite code: the account and the code's use are saved together, so
     * a code works exactly once and a failed sign-up leaves it unused. Without invite-only sign-up,
     * the code is ignored.
     */
    @Transactional
    public SignedInUser register(String email, String password, String displayName, String inviteCode) {
        var user = register(email, password, displayName);
        if (invites.inviteOnly()) {
            users.flush();
            invites.claim(inviteCode, user.id());
        }
        return user;
    }

    @Override
    @Transactional(readOnly = true)
    public SignedInUser loadUserByUsername(String email) {
        return users.findByEmail(UserAccount.normalizeEmail(email))
                .map(SignedInUser::of)
                .orElseThrow(() -> new UsernameNotFoundException("No account for that email"));
    }
}
