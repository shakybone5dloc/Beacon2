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
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AccountService(UserAccountRepository users, PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
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

    @Override
    @Transactional(readOnly = true)
    public SignedInUser loadUserByUsername(String email) {
        return users.findByEmail(UserAccount.normalizeEmail(email))
                .map(SignedInUser::of)
                .orElseThrow(() -> new UsernameNotFoundException("No account for that email"));
    }
}
