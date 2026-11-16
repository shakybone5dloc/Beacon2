package dev.beacon.api.auth;

import dev.beacon.TestcontainersConfiguration;
import dev.beacon.application.users.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/** The limits are kept in memory for the whole test context, so each test uses its own address and email. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "beacon.limits.login-per-ip=4",
        "beacon.limits.login-failures-per-account=3",
        "beacon.limits.register-per-ip=2"})
@Transactional
class LimitApiTests {

    @Autowired MockMvcTester mvc;
    @Autowired AccountService accounts;

    @Test
    void anAccountIsLockedAfterRepeatedWrongPasswordsEvenWithTheRightOne() {
        var email = email();
        accounts.register(email, "correct horse battery", null);

        for (int i = 0; i < 3; i++) {
            assertThat(login(address(), email, "wrong password!")).hasStatus(HttpStatus.UNAUTHORIZED);
        }

        // From anywhere, with the right password: locked for now.
        var locked = login(address(), email, "correct horse battery");
        assertThat(locked).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(locked).bodyJson().extractingPath("$.detail").asString().startsWith("Too many attempts. Try again in");
        assertThat(locked).headers().containsHeader("Retry-After");
        // Typed with different case, it's still the same account.
        assertThat(login(address(), email.toUpperCase(), "correct horse battery")).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void aSuccessfulSignInClearsTheAccountsFailures() {
        var email = email();
        accounts.register(email, "correct horse battery", null);

        assertThat(login(address(), email, "wrong password!")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(login(address(), email, "wrong password!")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(login(address(), email, "correct horse battery")).hasStatus(HttpStatus.OK);
        assertThat(login(address(), email, "wrong password!")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(login(address(), email, "wrong password!")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(login(address(), email, "correct horse battery")).hasStatus(HttpStatus.OK);
    }

    @Test
    void oneAddressCanOnlyTrySoManySignIns() {
        var from = address();
        for (int i = 0; i < 4; i++) {
            assertThat(login(from, email(), "wrong password!")).hasStatus(HttpStatus.UNAUTHORIZED);
        }

        assertThat(login(from, email(), "wrong password!")).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        // Another address is still fine.
        assertThat(login(address(), email(), "wrong password!")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void oneAddressCanOnlyTrySoManySignUps() {
        var from = address();
        var taken = email();
        assertThat(register(from, taken)).hasStatus(HttpStatus.CREATED);
        // A failed sign-up counts too (a request that isn't even valid never gets this far).
        assertThat(register(from, taken)).hasStatus(HttpStatus.BAD_REQUEST);

        assertThat(register(from, email())).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(register(address(), email())).hasStatus(HttpStatus.CREATED);
    }

    private MvcTestResult login(String from, String email, String password) {
        return mvc.post().uri("/api/auth/login").with(csrf())
                .with(request -> {
                    request.setRemoteAddr(from);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s", "rememberMe": false}
                        """.formatted(email, password))
                .exchange();
    }

    private MvcTestResult register(String from, String email) {
        return mvc.post().uri("/api/auth/register").with(csrf())
                .with(request -> {
                    request.setRemoteAddr(from);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "correct horse battery"}
                        """.formatted(email))
                .exchange();
    }

    private static String email() {
        return "limit-" + UUID.randomUUID() + "@example.test";
    }

    private static int next = 1;

    /** A fresh address for each use, so one test's attempts don't count against another's. */
    private static synchronized String address() {
        next++;
        return "10.77." + (next / 250) + "." + (next % 250 + 1);
    }
}
