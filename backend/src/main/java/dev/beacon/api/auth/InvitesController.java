package dev.beacon.api.auth;

import dev.beacon.application.users.Admins;
import dev.beacon.application.users.InviteService;
import dev.beacon.application.users.SignedInUser;
import dev.beacon.contracts.auth.CreateInviteRequest;
import dev.beacon.contracts.auth.InviteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/** Invite codes, for admins only; to anyone else these addresses don't exist. */
@Tag(name = "Admin")
@RestController
@RequestMapping("/api/admin/invites")
public class InvitesController {

    private final InviteService invites;
    private final Admins admins;

    public InvitesController(InviteService invites, Admins admins) {
        this.invites = invites;
        this.admins = admins;
    }

    @Operation(operationId = "listInvites")
    @GetMapping
    public List<InviteResponse> list(@AuthenticationPrincipal SignedInUser user) {
        requireAdmin(user);
        return invites.list();
    }

    @Operation(operationId = "createInvite")
    @ApiResponse(responseCode = "201", useReturnTypeSchema = true)
    @PostMapping
    public ResponseEntity<InviteResponse> create(@AuthenticationPrincipal SignedInUser user,
                                                 @Valid @RequestBody CreateInviteRequest body) {
        requireAdmin(user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(invites.create(user.id(), body.note(), body.days() == null ? 14 : body.days()));
    }

    @Operation(operationId = "revokeInvite")
    @PostMapping("/{id}/revoke")
    public ResponseEntity<InviteResponse> revoke(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID id) {
        requireAdmin(user);
        return ResponseEntity.of(invites.revoke(id));
    }

    private void requireAdmin(SignedInUser user) {
        if (!admins.isAdmin(user.email())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
