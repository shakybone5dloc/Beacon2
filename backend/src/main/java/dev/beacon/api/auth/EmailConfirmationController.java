package dev.beacon.api.auth;

import dev.beacon.application.users.AccountLinks;
import dev.beacon.application.users.SignedInUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Sends the signed-in user another link to confirm their email (L5). */
@Tag(name = "Account")
@RestController
@RequestMapping("/api/me")
public class EmailConfirmationController {

    private final AccountLinks links;

    public EmailConfirmationController(AccountLinks links) {
        this.links = links;
    }

    /** Does nothing when the email is already confirmed or the server can't send mail. */
    @Operation(operationId = "resendEmailConfirmation")
    @ApiResponse(responseCode = "202", description = "A new link is on its way")
    @ApiResponse(responseCode = "429", description = "Too many emails to this account this hour",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/confirm-email")
    public ResponseEntity<Void> resend(@AuthenticationPrincipal SignedInUser user) {
        links.sendConfirmation(user.id());
        return ResponseEntity.accepted().build();
    }
}
