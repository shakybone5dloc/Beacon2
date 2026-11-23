package dev.beacon.api.auth;

import dev.beacon.application.users.AdminUsers;
import dev.beacon.application.users.Admins;
import dev.beacon.application.users.SignedInUser;
import dev.beacon.contracts.auth.AdminUserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/** Every account, for admins only; to anyone else these addresses don't exist. */
@Tag(name = "Admin")
@RestController
@RequestMapping("/api/admin/users")
public class AdminUsersController {

    private final AdminUsers users;
    private final Admins admins;

    public AdminUsersController(AdminUsers users, Admins admins) {
        this.users = users;
        this.admins = admins;
    }

    @Operation(operationId = "listUsers")
    @GetMapping
    public List<AdminUserResponse> list(@AuthenticationPrincipal SignedInUser user) {
        requireAdmin(user);
        return users.list();
    }

    @Operation(operationId = "disableUser")
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", description = "An admin can't be switched off here",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/{id}/disable")
    public ResponseEntity<AdminUserResponse> disable(@AuthenticationPrincipal SignedInUser user,
                                                     @PathVariable UUID id) {
        requireAdmin(user);
        try {
            return ResponseEntity.of(users.disable(id));
        } catch (AdminUsers.NotAllowedException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @Operation(operationId = "enableUser")
    @PostMapping("/{id}/enable")
    public ResponseEntity<AdminUserResponse> enable(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID id) {
        requireAdmin(user);
        return ResponseEntity.of(users.enable(id));
    }

    private void requireAdmin(SignedInUser user) {
        if (!admins.isAdmin(user.email())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
