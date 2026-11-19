package dev.beacon.api.auth;

import dev.beacon.api.errors.RequestValidationException;
import dev.beacon.application.users.AccountLinks;
import dev.beacon.application.users.AccountService;
import dev.beacon.application.users.Admins;
import dev.beacon.application.users.AttemptLimiter;
import dev.beacon.application.users.InviteService;
import dev.beacon.application.users.SessionRevocations;
import dev.beacon.application.users.SignedInUser;
import dev.beacon.application.users.TooManyAttemptsException;
import dev.beacon.contracts.auth.RegistrationResponse;
import dev.beacon.domain.users.UserAccount;
import dev.beacon.contracts.auth.CurrentUserResponse;
import dev.beacon.contracts.auth.ConfirmEmailRequest;
import dev.beacon.contracts.auth.LoginRequest;
import dev.beacon.contracts.auth.NewPasswordRequest;
import dev.beacon.contracts.auth.PasswordResetRequest;
import dev.beacon.contracts.auth.RegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/** Register, sign in and look up the signed-in user. Sign-out is handled by the security filter chain. */
@Tag(name = "Auth")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AccountService accounts;
    private final AccountLinks accountLinks;
    private final InviteService invites;
    private final Admins admins;
    private final AttemptLimiter limiter;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository;
    private final RememberMeServices rememberMe;

    public AuthController(AccountService accounts, AccountLinks accountLinks, InviteService invites, Admins admins,
                          AttemptLimiter limiter, AuthenticationManager authenticationManager,
                          SecurityContextRepository contextRepository, RememberMeServices rememberMe) {
        this.accounts = accounts;
        this.accountLinks = accountLinks;
        this.invites = invites;
        this.admins = admins;
        this.limiter = limiter;
        this.authenticationManager = authenticationManager;
        this.contextRepository = contextRepository;
        this.rememberMe = rememberMe;
    }

    /** Touching the token makes Spring write the XSRF-TOKEN cookie the frontend sends back. */
    @Operation(operationId = "getCsrfToken")
    @ApiResponse(responseCode = "204")
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(@Parameter(hidden = true) CsrfToken token) {
        token.getToken();
        return ResponseEntity.noContent().build();
    }

    /** Whether signing up needs an invite code, for the sign-up page. */
    @Operation(operationId = "getRegistration")
    @GetMapping("/registration")
    public RegistrationResponse registration() {
        return new RegistrationResponse(invites.inviteOnly(), accountLinks.canEmail());
    }

    @Operation(operationId = "register")
    @ApiResponse(responseCode = "201", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "429", description = "Too many sign-ups from this address",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/register")
    public ResponseEntity<CurrentUserResponse> register(@Valid @RequestBody RegisterRequest body,
                                                        HttpServletRequest request, HttpServletResponse response) {
        var address = request.getRemoteAddr();
        limiter.blockedFor(AttemptLimiter.Kind.REGISTER_IP, address).ifPresent(wait -> {
            throw new TooManyAttemptsException(wait);
        });
        // Every attempt counts, so invite codes can't be guessed one after another.
        limiter.record(AttemptLimiter.Kind.REGISTER_IP, address);
        if (invites.inviteOnly() && (body.inviteCode() == null || body.inviteCode().isBlank())) {
            throw new RequestValidationException("inviteCode", "Beacon is invite-only for now. Enter your invite code.");
        }
        SignedInUser user;
        try {
            user = accounts.register(body.email(), body.password(), body.displayName(), body.inviteCode());
        } catch (AccountService.EmailTakenException ex) {
            throw new RequestValidationException("email", ex.getMessage());
        } catch (InviteService.InvalidInviteException ex) {
            throw new RequestValidationException("inviteCode", ex.getMessage());
        }
        user.eraseCredentials();
        signIn(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()), request, response);
        try {
            accountLinks.sendConfirmation(user.id());
        } catch (TooManyAttemptsException ignored) {
            // A brand-new account can't be over the limit; if it somehow is, the banner offers a resend.
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(user));
    }

    @Operation(operationId = "login")
    @ApiResponse(responseCode = "200", description = "Signed in",
            content = @Content(schema = @Schema(implementation = CurrentUserResponse.class)))
    @ApiResponse(responseCode = "401", description = "Wrong email or password",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "429", description = "Too many attempts from this address or for this account",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest body,
                                   HttpServletRequest request, HttpServletResponse response) {
        var address = request.getRemoteAddr();
        var account = UserAccount.normalizeEmail(body.email());
        // Checked before the password, so a flood of attempts never reaches the (slow) hash.
        limiter.blockedFor(AttemptLimiter.Kind.LOGIN_IP, address)
                .or(() -> limiter.blockedFor(AttemptLimiter.Kind.LOGIN_ACCOUNT, account))
                .ifPresent(wait -> {
                    throw new TooManyAttemptsException(wait);
                });
        limiter.record(AttemptLimiter.Kind.LOGIN_IP, address);
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(body.email(), body.password()));
            limiter.clear(AttemptLimiter.Kind.LOGIN_ACCOUNT, account);
        } catch (AuthenticationException ex) {
            limiter.record(AttemptLimiter.Kind.LOGIN_ACCOUNT, account);
            // Same answer for a wrong password and an unknown email, so emails can't be probed.
            var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Email or password is incorrect.");
            problem.setTitle("Sign-in failed");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
        }
        signIn(authentication, request, response);
        if (body.rememberMe()) rememberMe.loginSuccess(request, response, authentication);
        return ResponseEntity.ok(toResponse((SignedInUser) authentication.getPrincipal()));
    }

    /**
     * Emails a reset link if the address has an account. Always 202, so it can't be used to find
     * out who has an account; limited per address.
     */
    @Operation(operationId = "requestPasswordReset")
    @ApiResponse(responseCode = "202", description = "If the address has an account, a link is on its way")
    @ApiResponse(responseCode = "429", description = "Too many requests from this address",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/password-reset")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest body,
                                                     HttpServletRequest request) {
        var address = request.getRemoteAddr();
        limiter.blockedFor(AttemptLimiter.Kind.EMAIL_REQUEST_IP, address).ifPresent(wait -> {
            throw new TooManyAttemptsException(wait);
        });
        limiter.record(AttemptLimiter.Kind.EMAIL_REQUEST_IP, address);
        accountLinks.requestPasswordReset(body.email());
        return ResponseEntity.accepted().build();
    }

    /** Sets a new password from a reset link and signs out every other device. */
    @Operation(operationId = "resetPassword")
    @ApiResponse(responseCode = "204")
    @ApiResponse(responseCode = "400", description = "The link expired, was used, or the password is too short",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody NewPasswordRequest body) {
        try {
            accountLinks.resetPassword(body.token(), body.password());
        } catch (AccountLinks.InvalidLinkException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        return ResponseEntity.noContent().build();
    }

    /** Confirms an email from the link sent to it; works signed in or not. */
    @Operation(operationId = "confirmEmail")
    @ApiResponse(responseCode = "204")
    @ApiResponse(responseCode = "400", description = "The link expired or was already used",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/confirm-email")
    public ResponseEntity<Void> confirmEmail(@Valid @RequestBody ConfirmEmailRequest body) {
        try {
            accountLinks.confirmEmail(body.token());
        } catch (AccountLinks.InvalidLinkException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        return ResponseEntity.noContent().build();
    }

    @Operation(operationId = "getCurrentUser")
    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal SignedInUser user) {
        return toResponse(user);
    }

    /** Stores the authentication in a fresh session id (guarding against session fixation). */
    private void signIn(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        request.getSession(true);
        request.changeSessionId();
        request.getSession().setAttribute(SessionRevocations.SIGNED_IN_AT, Instant.now());
        var context = SecurityContextHolder.getContextHolderStrategy().createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.getContextHolderStrategy().setContext(context);
        contextRepository.saveContext(context, request, response);
    }

    private CurrentUserResponse toResponse(SignedInUser user) {
        var confirmationNeeded = accountLinks.canEmail() && !accountLinks.isEmailConfirmed(user.id());
        return new CurrentUserResponse(user.id(), user.email(), user.displayName(), admins.isAdmin(user.email()),
                confirmationNeeded);
    }
}
