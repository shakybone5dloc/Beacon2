package dev.beacon.infrastructure.persistence;

import dev.beacon.domain.users.AccountLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AccountLinkRepository extends JpaRepository<AccountLink, UUID> {

    Optional<AccountLink> findByTokenHashAndPurpose(String tokenHash, AccountLink.Purpose purpose);

    /**
     * Marks the link used, if it still can be; 1 when it was, 0 when not. One statement, so the
     * same link followed twice at once works only once.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update AccountLink l set l.usedAt = :now
            where l.tokenHash = :tokenHash and l.purpose = :purpose and l.usedAt is null and l.expiresAt > :now
            """)
    int use(String tokenHash, AccountLink.Purpose purpose, Instant now);

    /** Drops a user's other links of one kind, so only the newest one works. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from AccountLink l where l.userId = :userId and l.purpose = :purpose")
    void deleteAllForUser(UUID userId, AccountLink.Purpose purpose);
}
