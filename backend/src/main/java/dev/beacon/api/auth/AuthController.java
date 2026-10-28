package dev.beacon.api.auth;

import dev.beacon.api.errors.RequestValidationException;
import dev.beacon.application.users.AccountService;
import dev.beacon.application.users.SignedInUser;
import dev.beacon.contracts.auth.CurrentUserResponse;
import dev.beacon.contracts.auth.LoginRequest;
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
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

/** Register, sign in and look up the signed-in user. Sign-out is handled by the security filter chain. */
@Tag(name = "Auth")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AccountService accounts;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository;

    public AuthController(AccountService accounts, AuthenticationManager authenticationManager,
                          SecurityContextRepository contextRepository) {
        this.accounts = accounts;
        this.authenticationManager = authenticationManager;
        this.contextRepository = contextRepository;
    }

    /** Touching the token makes Spring write the XSRF-TOKEN cookie the frontend sends back. */
    @Operation(operationId = "getCsrfToken")
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(@Parameter(hidden = true) CsrfToken token) {
        token.getToken();
        return ResponseEntity.noContent().build();
    }

    @Operation(operationId = "register")
    @PostMapping("/register")
    public ResponseEntity<CurrentUserResponse> register(@Valid @RequestBody RegisterRequest body,
                                                        HttpServletRequest request, HttpServletResponse response) {
        SignedInUser user;
        try {
            user = accounts.register(body.email(), body.password(), body.displayName());
        } catch (AccountService.EmailTakenException ex) {
            throw new RequestValidationException("email", ex.getMessage());
        }
        user.eraseCredentials();
        signIn(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()), request, response);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(user));
    }

    @Operation(operationId = "login")
    @ApiResponse(responseCode = "200", description = "Signed in",
            content = @Content(schema = @Schema(implementation = CurrentUserResponse.class)))
    @ApiResponse(responseCode = "401", description = "Wrong email or password",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest body,
                                   HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(body.email(), body.password()));
        } catch (AuthenticationException ex) {
            // Same answer for a wrong password and an unknown email, so emails can't be probed.
            var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Email or password is incorrect.");
            problem.setTitle("Sign-in failed");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
        }
        signIn(authentication, request, response);
        return ResponseEntity.ok(toResponse((SignedInUser) authentication.getPrincipal()));
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
        var context = SecurityContextHolder.getContextHolderStrategy().createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.getContextHolderStrategy().setContext(context);
        contextRepository.saveContext(context, request, response);
    }

    private static CurrentUserResponse toResponse(SignedInUser user) {
        return new CurrentUserResponse(user.id(), user.email(), user.displayName());
    }
}
