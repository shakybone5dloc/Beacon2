package dev.beacon.api.auth;

import dev.beacon.DocumentTestDoubles;
import dev.beacon.DocumentTestDoubles.FakeMailer;
import dev.beacon.TestcontainersConfiguration;
import dev.beacon.domain.users.AccountLink;
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
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/** Password reset and email confirmation (L5), through the API, with the fake mailer. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, DocumentTestDoubles.class})
@Transactional
class AccountLinkApiTests {

    private static final String PASSWORD = "correct horse battery";
    private static final String NEW_PASSWORD = "a brand new passphrase";
    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Autowired MockMvcTester mvc;
    @Autowired FakeMailer mailer;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entities;

    @BeforeEach
    void clearMail() {
        mailer.reset();
        mailer.configured = true;
    }

    @Test
    void signingUpSendsAConfirmationLinkThatWorksOnce() {
        var email = email();
        var session = session(register(email));

        assertThat(mailer.sent).singleElement().satisfies(mail -> {
            assertThat(mail.to()).isEqualTo(email);
            assertThat(mail.subject()).isEqualTo("Confirm your email for Beacon");
            assertThat(mail.text()).contains("/confirm-email?token=");
        });
        assertThat(me(session)).bodyJson().extractingPath("$.emailConfirmationNeeded").isEqualTo(true);

        var token = lastToken();
        assertThat(post("/api/auth/confirm-email", "{\"token\": \"%s\"}".formatted(token)))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(me(session)).bodyJson().extractingPath("$.emailConfirmationNeeded").isEqualTo(false);
        assertThat(post("/api/auth/confirm-email", "{\"token\": \"%s\"}".formatted(token)))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail").asString().contains("expired or was already used");
    }

    @Test
    void aSignedInUserCanAskForAnotherConfirmationUntilTheHourlyLimit() {
        var session = session(register(email()));

        // Sign-up sent the first; two more fit in the hour, the next is refused.
        for (int i = 0; i < 2; i++) {
            assertThat(mvc.post().uri("/api/me/confirm-email").session(session).with(csrf()).exchange())
                    .hasStatus(HttpStatus.ACCEPTED);
        }
        assertThat(mailer.sent).hasSize(3);
        assertThat(mvc.post().uri("/api/me/confirm-email").session(session).with(csrf()).exchange())
                .hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(mailer.sent).hasSize(3);
    }

    @Test
    void askingForAResetAnswersTheSameWhetherOrNotTheAccountExists() {
        assertThat(requestReset(email())).hasStatus(HttpStatus.ACCEPTED);
        assertThat(mailer.sent).isEmpty();

        var email = email();
        register(email);
        mailer.reset();
        assertThat(requestReset(email.toUpperCase())).hasStatus(HttpStatus.ACCEPTED);
        assertThat(mailer.sent).singleElement().satisfies(mail -> {
            assertThat(mail.to()).isEqualTo(email);
            assertThat(mail.subject()).isEqualTo("Reset your Beacon password");
            assertThat(mail.text()).contains("/reset-password?token=");
        });
    }

    @Test
    void aResetLinkSetsTheNewPasswordOnceAndSignsOutEverywhereElse() {
        var email = email();
        var signedIn = session(register(email));
        var remembered = login(email, PASSWORD, true).getResponse().getCookie("remember-me");
        assertThat(remembered).isNotNull();
        requestReset(email);
        var token = lastToken();

        assertThat(post("/api/auth/password-reset/confirm",
                "{\"token\": \"%s\", \"password\": \"short\"}".formatted(token)))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(post("/api/auth/password-reset/confirm",
                "{\"token\": \"%s\", \"password\": \"%s\"}".formatted(token, NEW_PASSWORD)))
                .hasStatus(HttpStatus.NO_CONTENT);

        // Sessions and remember-me cookies from before the reset no longer work.
        assertThat(me(signedIn)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/api/auth/me").cookie(new Cookie("remember-me", remembered.getValue())).exchange())
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(login(email, PASSWORD, false)).hasStatus(HttpStatus.UNAUTHORIZED);
        var fresh = login(email, NEW_PASSWORD, false);
        assertThat(fresh).hasStatus(HttpStatus.OK);
        // The link came to their inbox, so the email counts as confirmed.
        assertThat(me(session(fresh))).bodyJson().extractingPath("$.emailConfirmationNeeded").isEqualTo(false);

        assertThat(post("/api/auth/password-reset/confirm",
                "{\"token\": \"%s\", \"password\": \"%s\"}".formatted(token, "yet another passphrase")))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void onlyTheNewestResetLinkWorksAndOldOnesExpire() {
        var email = email();
        register(email);
        requestReset(email);
        var first = lastToken();
        requestReset(email);
        var second = lastToken();

        assertThat(resetWith(first)).hasStatus(HttpStatus.BAD_REQUEST);

        entities.flush();
        jdbc.update("update account_links set expires_at = now() - interval '1 minute' where token_hash = ?",
                AccountLink.hash(second));
        assertThat(resetWith(second)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void anAccountGetsAtMostThreeOfTheseEmailsAnHour() {
        var email = email();
        register(email);
        for (int i = 0; i < 4; i++) {
            assertThat(requestReset(email)).hasStatus(HttpStatus.ACCEPTED);
        }
        // The confirmation from sign-up and two resets; the rest are dropped without saying so.
        assertThat(mailer.sent).hasSize(3);
    }

    @Test
    void onlyAHashOfTheTokenIsStored() {
        var email = email();
        register(email);
        requestReset(email);
        var token = lastToken();
        entities.flush();

        var stored = jdbc.queryForList("select token_hash from account_links", String.class);
        assertThat(stored).contains(AccountLink.hash(token)).doesNotContain(token);
    }

    @Test
    void withoutMailNothingIsSentAndSignInOffersNoReset() {
        mailer.configured = false;
        var session = session(register(email()));

        assertThat(mailer.sent).isEmpty();
        assertThat(me(session)).bodyJson().extractingPath("$.emailConfirmationNeeded").isEqualTo(false);
        assertThat(mvc.get().uri("/api/auth/registration").exchange())
                .bodyJson().extractingPath("$.passwordReset").isEqualTo(false);
    }

    private MvcTestResult register(String email) {
        return post("/api/auth/register", "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, PASSWORD));
    }

    private MvcTestResult login(String email, String password, boolean rememberMe) {
        return post("/api/auth/login", "{\"email\": \"%s\", \"password\": \"%s\", \"rememberMe\": %s}"
                .formatted(email, password, rememberMe));
    }

    private MvcTestResult requestReset(String email) {
        return post("/api/auth/password-reset", "{\"email\": \"%s\"}".formatted(email));
    }

    private MvcTestResult resetWith(String token) {
        return post("/api/auth/password-reset/confirm",
                "{\"token\": \"%s\", \"password\": \"%s\"}".formatted(token, NEW_PASSWORD));
    }

    private MvcTestResult me(MockHttpSession session) {
        return mvc.get().uri("/api/auth/me").session(session).exchange();
    }

    private MvcTestResult post(String uri, String json) {
        return mvc.post().uri(uri).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private String lastToken() {
        var matcher = TOKEN.matcher(mailer.sent.getLast().text());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private static MockHttpSession session(MvcTestResult result) {
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private static String email() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
