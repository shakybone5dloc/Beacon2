package dev.beacon.config;

import dev.beacon.application.users.AccountService;
import dev.beacon.application.users.ActivityTracker;
import dev.beacon.application.users.SessionRevocations;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.DisableEncodeUrlFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationFilter;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices.RememberMeTokenAlgorithm;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;

import java.time.Duration;

/**
 * Session-cookie sign-in for the Angular app. Everything under /api needs a signed-in
 * user except registering, signing in and fetching a CSRF token. Unauthenticated calls
 * get a bare 401, never a redirect to a login page.
 */
@Configuration
public class SecurityConfig {

    /** How long "Remember me" keeps someone signed in. */
    static final Duration REMEMBER_FOR = Duration.ofDays(30);

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contextRepository,
                                            TokenBasedRememberMeServices rememberMe,
                                            SessionRevocations revocations,
                                            ActivityTracker activity) throws Exception {
        http
                // Gives the request an id for the logs, the response header and error bodies; first, so
                // everything after it logs with the id.
                .addFilterBefore(new RequestIdFilter(), DisableEncodeUrlFilter.class)
                // Puts the signed-in user's id in the logs (never the email).
                .addFilterAfter(new UserIdLogFilter(), RememberMeAuthenticationFilter.class)
                // Ends sessions that signed in before a password reset or the account was switched off.
                .addFilterAfter(new RevokedSessionFilter(revocations), SecurityContextHolderFilter.class)
                // Notes when each account was last used, for the admin's user list.
                .addFilterAfter(new ActivityFilter(activity), RememberMeAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/password-reset",
                                "/api/auth/password-reset/confirm", "/api/auth/confirm-email").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf", "/api/auth/registration", "/api/site").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                // Cookie-based token Angular sends back automatically (XSRF-TOKEN -> X-XSRF-TOKEN).
                .csrf(csrf -> csrf.spa())
                .securityContext(context -> context.securityContextRepository(contextRepository))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                // Beacon's own pages may show its files in a frame (the saved posting PDF on the
                // application page); other sites still can't. Spring's default, DENY, blocks both.
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                        .contentSecurityPolicy(csp -> csp.policyDirectives("frame-ancestors 'self'")))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                // Signs the user back in from the remember-me cookie when the session is gone; sign-out clears it.
                .rememberMe(remember -> remember.rememberMeServices(rememberMe).key(rememberMe.getKey()))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)));
        return http.build();
    }

    /**
     * A signed cookie (SHA-256 over the email, expiry, password hash and the server secret),
     * so it survives server restarts with nothing stored. Changing the password invalidates it.
     * Set only when the sign-in asks for it, so it always remembers once called.
     */
    @Bean
    TokenBasedRememberMeServices rememberMeServices(AccountService accounts,
                                                    @Value("${beacon.secret-key:}") String secretKey) {
        var services = new TokenBasedRememberMeServices(secretKey, accounts, RememberMeTokenAlgorithm.SHA256);
        services.setAlwaysRemember(true);
        services.setTokenValiditySeconds((int) REMEMBER_FOR.toSeconds());
        return services;
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AccountService accounts, PasswordEncoder passwordEncoder) {
        var provider = new DaoAuthenticationProvider(accounts);
        // A switched-off account is refused only after its password checks out, so the refusal
        // tells nothing to someone guessing; the default checks it first.
        provider.setPreAuthenticationChecks(user -> { });
        provider.setPostAuthenticationChecks(user -> {
            if (!user.isEnabled()) throw new DisabledException("This account has been switched off.");
        });
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
