package dev.beacon.api.auth;

import dev.beacon.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AuthApiTests {

    private static final String PASSWORD = "correct horse battery";

    @Autowired MockMvcTester mvc;

    @Test
    void signedOutCallsGet401() {
        assertThat(mvc.get().uri("/api/applications").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/api/auth/me").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    /**
     * Only checks the endpoint is open: once any test uses {@code csrf()}, Spring swaps the
     * shared filter's token store for a test stub, so the real XSRF-TOKEN cookie can't be
     * asserted here. The browser check covers it.
     */
    @Test
    void csrfEndpointIsOpenToSignedOutUsers() {
        assertThat(mvc.get().uri("/api/auth/csrf").exchange()).hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    void registerSignsInAndMeReturnsTheUser() {
        var email = email();
        var registered = register(email.toUpperCase(), PASSWORD);

        assertThat(registered).hasStatus(HttpStatus.CREATED);
        assertThat(registered).bodyJson().extractingPath("$.email").isEqualTo(email);

        var me = mvc.get().uri("/api/auth/me").session(session(registered)).exchange();
        assertThat(me).hasStatus(HttpStatus.OK);
        assertThat(me).bodyJson().extractingPath("$.email").isEqualTo(email);
    }

    @Test
    void registerRejectsADuplicateEmailAndAShortPassword() {
        var email = email();
        register(email, PASSWORD);

        var duplicate = register(email.toUpperCase(), PASSWORD);
        assertThat(duplicate).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(duplicate).bodyJson().extractingPath("$.errors.email").isNotNull();

        var shortPassword = register(email(), "short");
        assertThat(shortPassword).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(shortPassword).bodyJson().extractingPath("$.errors.password").isNotNull();
    }

    @Test
    void loginWorksWithTheRightPasswordOnly() {
        var email = email();
        register(email, PASSWORD);

        var wrong = login(email, "not the password at all");
        assertThat(wrong).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(wrong).bodyJson().extractingPath("$.detail").isEqualTo("Email or password is incorrect.");

        var unknown = login(email(), PASSWORD);
        assertThat(unknown).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(unknown).bodyJson().extractingPath("$.detail").isEqualTo("Email or password is incorrect.");

        var right = login(email, PASSWORD);
        assertThat(right).hasStatus(HttpStatus.OK);
        assertThat(mvc.get().uri("/api/applications").session(session(right)).exchange()).hasStatus(HttpStatus.OK);
    }

    @Test
    void logoutEndsTheSession() {
        var session = session(register(email(), PASSWORD));

        assertThat(mvc.post().uri("/api/auth/logout").session(session).with(csrf()).exchange())
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/auth/me").session(session).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void writesWithoutACsrfTokenAreRejected() {
        var session = session(register(email(), PASSWORD));

        var result = mvc.post().uri("/api/applications").session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"company": "Acme", "role": "Engineer"}
                        """)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
    }

    private MvcTestResult register(String email, String password) {
        return mvc.post().uri("/api/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password))
                .exchange();
    }

    private MvcTestResult login(String email, String password) {
        return mvc.post().uri("/api/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password))
                .exchange();
    }

    private static MockHttpSession session(MvcTestResult result) {
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private static String email() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
