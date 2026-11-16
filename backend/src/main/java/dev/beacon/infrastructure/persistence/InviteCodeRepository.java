package dev.beacon.infrastructure.persistence;

import dev.beacon.domain.users.InviteCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface InviteCodeRepository extends JpaRepository<InviteCode, UUID> {

    List<InviteCode> findAllByOrderByCreatedAtDesc();

    /**
     * Marks the code used by this account, if it's still usable; 1 when it was, 0 when not.
     * One statement, so two people signing up with the same code at once can't both have it.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update InviteCode i set i.usedBy = :userId, i.usedAt = :now
            where i.code = :code and i.usedAt is null and i.revokedAt is null and i.expiresAt > :now
            """)
    int claim(String code, UUID userId, Instant now);
}
