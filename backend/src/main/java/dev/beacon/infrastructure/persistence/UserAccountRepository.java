package dev.beacon.infrastructure.persistence;

import dev.beacon.domain.users.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    /** Expects an already-normalized (lower-cased) email. */
    Optional<UserAccount> findByEmail(String email);

    boolean existsByEmail(String email);
}
