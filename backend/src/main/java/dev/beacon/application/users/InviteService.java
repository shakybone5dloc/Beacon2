package dev.beacon.application.users;

import dev.beacon.contracts.auth.InviteResponse;
import dev.beacon.domain.users.InviteCode;
import dev.beacon.infrastructure.persistence.InviteCodeRepository;
import dev.beacon.infrastructure.persistence.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Invite codes for the invite-only beta: made and revoked by admins, used once at sign-up. */
@Service
public class InviteService {

    static final int MAX_DAYS = 90;

    private final InviteCodeRepository invites;
    private final UserAccountRepository users;
    private final boolean inviteOnly;
    private final Clock clock;

    public InviteService(InviteCodeRepository invites, UserAccountRepository users,
                         @Value("${beacon.registration.invite-only:true}") boolean inviteOnly, Clock clock) {
        this.invites = invites;
        this.users = users;
        this.inviteOnly = inviteOnly;
        this.clock = clock;
    }

    /** Whether signing up needs an invite code. */
    public boolean inviteOnly() {
        return inviteOnly;
    }

    @Transactional(readOnly = true)
    public List<InviteResponse> list() {
        var now = Instant.now(clock);
        return invites.findAllByOrderByCreatedAtDesc().stream().map(invite -> toResponse(invite, now)).toList();
    }

    /** @param days how long the code works, 1 to 90 */
    @Transactional
    public InviteResponse create(UUID adminId, String note, int days) {
        var now = Instant.now(clock);
        var invite = InviteCode.create(adminId, note, now, now.plus(Duration.ofDays(Math.clamp(days, 1, MAX_DAYS))));
        invites.saveAndFlush(invite);
        return toResponse(invite, now);
    }

    /** Stops an unused code from working. Empty when there's no such code. */
    @Transactional
    public Optional<InviteResponse> revoke(UUID id) {
        var now = Instant.now(clock);
        return invites.findById(id).map(invite -> {
            invite.revoke(now);
            return toResponse(invites.saveAndFlush(invite), now);
        });
    }

    /**
     * Marks the code used by this new account. Called in the account's own transaction, so a
     * failed sign-up leaves the code unused. Throws {@link InvalidInviteException} when the code
     * doesn't exist or can't be used any more; which of those isn't said, so codes can't be probed.
     */
    @Transactional
    public void claim(String typedCode, UUID userId) {
        var code = InviteCode.normalize(typedCode);
        if (code.isEmpty() || invites.claim(code, userId, Instant.now(clock)) != 1) {
            throw new InvalidInviteException();
        }
    }

    private InviteResponse toResponse(InviteCode invite, Instant now) {
        var usedBy = invite.getUsedBy() == null ? null
                : users.findById(invite.getUsedBy()).map(user -> user.getEmail()).orElse(null);
        String state;
        if (invite.getUsedAt() != null) state = "USED";
        else if (invite.getRevokedAt() != null) state = "REVOKED";
        else if (!now.isBefore(invite.getExpiresAt())) state = "EXPIRED";
        else state = "OPEN";
        return new InviteResponse(invite.getId(), invite.getCode(), invite.getNote(), state, invite.getCreatedAt(),
                invite.getExpiresAt(), usedBy, invite.getUsedAt(), invite.getRevokedAt());
    }

    /** The invite code can't be used: unknown, used, expired or revoked. */
    public static class InvalidInviteException extends RuntimeException {
        public InvalidInviteException() {
            super("That invite code isn't valid, or it has already been used.");
        }
    }
}
