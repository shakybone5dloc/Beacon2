package dev.beacon.api.auth;

import dev.beacon.TestcontainersConfiguration;
import dev.beacon.application.users.AccountService;
import dev.beacon.application.users.SignedInUser;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/** The admin's user list, and switching accounts off and on. The test admin is x@example.test. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AdminUsersApiTests {

    private static final String PASSWORD = "correct horse battery";

    @Autowired MockMvcTester mvc;
    @Autowired AccountService accounts;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entities;

    SignedInUser admin;

    @BeforeEach
    void makeAdmin() {
        admin = accounts.register("x@example.test", PASSWORD, "Admin");
    }

    @Test
    void onlyAdminsSeeTheListOrCanSwitchAccountsOff() {
        var someone = accounts.register(email(), PASSWORD, null);

        assertThat(mvc.get().uri("/api/admin/users").with(user(someone)).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.post().uri("/api/admin/users/{id}/disable", admin.id()).with(user(someone)).with(csrf())
                .exchange()).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void theListShowsEachAccountWithItsActivity() {
        var email = email();
        var session = session(login(register(email), email, PASSWORD, false));
        mvc.post().uri("/api/applications").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"company\": \"Acme\", \"role\": \"Engineer\"}").exchange();

        var list = mvc.get().uri("/api/admin/users").with(user(admin)).exchange();
        assertThat(list).hasStatus(HttpStatus.OK);
        var row = "$[?(@.email == '%s')]".formatted(email);
        assertThat(list).bodyJson().extractingPath(row + ".applicationCount").asArray().containsExactly(1);
        assertThat(list).bodyJson().extractingPath(row + ".admin").asArray().containsExactly(false);
        assertThat(list).bodyJson().extractingPath(row + ".disabledAt").asArray().containsExactly((Object) null);
        assertThat(list).bodyJson().extractingPath(row + ".lastActiveAt").asArray().doesNotContainNull();
        assertThat(list).bodyJson().extractingPath("$[?(@.email == 'x@example.test')].admin").asArray()
                .containsExactly(true);
    }

    @Test
    void aSwitchedOffAccountCantSignInAndItsSessionsEnd() {
        var email = email();
        var id = register(email);
        var session = session(login(id, email, PASSWORD, false));
        var remembered = login(id, email, PASSWORD, true).getResponse().getCookie("remember-me");

        assertThat(mvc.post().uri("/api/admin/users/{id}/disable", id).with(user(admin)).with(csrf()).exchange())
                .hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.disabledAt").isNotNull();
        entities.flush();

        assertThat(me(session)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/api/auth/me").cookie(new Cookie("remember-me", remembered.getValue())).exchange())
                .hasStatus(HttpStatus.UNAUTHORIZED);
        // The right password is told why; a wrong one learns nothing new.
        assertThat(login(id, email, PASSWORD, false)).hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson().extractingPath("$.detail").asString().contains("switched off");
        assertThat(login(id, email, "not the password at all", false)).hasStatus(HttpStatus.UNAUTHORIZED);

        assertThat(mvc.post().uri("/api/admin/users/{id}/enable", id).with(user(admin)).with(csrf()).exchange())
                .hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.disabledAt").isNull();
        entities.flush();
        assertThat(login(id, email, PASSWORD, false)).hasStatus(HttpStatus.OK);
    }

    @Test
    void anAdminCantBeSwitchedOffHere() {
        assertThat(mvc.post().uri("/api/admin/users/{id}/disable", admin.id()).with(user(admin)).with(csrf())
                .exchange())
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail").asString().contains("BEACON_ADMIN_EMAILS");
        assertThat(mvc.post().uri("/api/admin/users/{id}/disable", UUID.randomUUID()).with(user(admin)).with(csrf())
                .exchange()).hasStatus(HttpStatus.NOT_FOUND);
    }

    private UUID register(String email) {
        var id = accounts.register(email, PASSWORD, null).id();
        entities.flush();
        return id;
    }

    private MvcTestResult login(UUID id, String email, String password, boolean rememberMe) {
        return mvc.post().uri("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\", \"rememberMe\": %s}"
                        .formatted(email, password, rememberMe))
                .exchange();
    }

    private MvcTestResult me(MockHttpSession session) {
        return mvc.get().uri("/api/auth/me").session(session).exchange();
    }

    private static MockHttpSession session(MvcTestResult result) {
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private static String email() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
