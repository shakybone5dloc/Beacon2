package dev.beacon.api.auth;

import dev.beacon.api.errors.RequestValidationException;
import dev.beacon.application.users.AccountData;
import dev.beacon.application.users.AttemptLimiter;
import dev.beacon.application.users.SignedInUser;
import dev.beacon.application.users.TooManyAttemptsException;
import dev.beacon.contracts.auth.DeleteAccountRequest;
import dev.beacon.infrastructure.persistence.UserAccountRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.Clock;
import java.time.LocalDate;

/** The signed-in user's whole account (L3): download everything, or delete it all. */
@Tag(name = "Account")
@RestController
@RequestMapping("/api/me")
public class AccountController {

    private final AccountData data;
    private final UserAccountRepository users;
    private final PasswordEncoder passwords;
    private final AttemptLimiter limiter;
    private final TokenBasedRememberMeServices rememberMe;
    private final Clock clock;

    public AccountController(AccountData data, UserAccountRepository users, PasswordEncoder passwords,
                             AttemptLimiter limiter, TokenBasedRememberMeServices rememberMe, Clock clock) {
        this.data = data;
        this.users = users;
        this.passwords = passwords;
        this.limiter = limiter;
        this.rememberMe = rememberMe;
        this.clock = clock;
    }

    /** A zip of everything the user has in Beacon: their data as JSON, and their files. */
    @Operation(operationId = "exportAccount")
    @ApiResponse(responseCode = "200", description = "A zip file",
            content = @Content(mediaType = "application/zip", schema = @Schema(type = "string", format = "binary")))
    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(@AuthenticationPrincipal SignedInUser user) {
        var filename = "beacon-export-" + LocalDate.now(clock) + ".zip";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(out -> data.export(user.id(), out));
    }

    /**
     * Deletes the account and everything in it, for good, then signs out. Takes the current
     * password, so a session left open can't do it; wrong passwords count toward the account's
     * sign-in lockout.
     */
    @Operation(operationId = "deleteAccount")
    @ApiResponse(responseCode = "204")
    @DeleteMapping
    public ResponseEntity<Void> delete(@AuthenticationPrincipal SignedInUser user,
                                       @Valid @RequestBody DeleteAccountRequest body,
                                       HttpServletRequest request, HttpServletResponse response) {
        var account = user.email();
        limiter.blockedFor(AttemptLimiter.Kind.LOGIN_ACCOUNT, account).ifPresent(wait -> {
            throw new TooManyAttemptsException(wait);
        });
        var hash = users.findById(user.id()).orElseThrow().getPasswordHash();
        if (!passwords.matches(body.password(), hash)) {
            limiter.record(AttemptLimiter.Kind.LOGIN_ACCOUNT, account);
            throw new RequestValidationException("password", "That password isn't right.");
        }
        data.delete(user.id());
        limiter.clear(AttemptLimiter.Kind.LOGIN_ACCOUNT, account);

        // Sign out: end the session and clear the remember-me cookie.
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        rememberMe.logout(request, response, authentication);
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }
}
