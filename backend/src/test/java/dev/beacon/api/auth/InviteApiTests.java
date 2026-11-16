package dev.beacon.api.auth;

import com.jayway.jsonpath.JsonPath;
import dev.beacon.SignedInApiTests;
import dev.beacon.TestcontainersConfiguration;
import dev.beacon.application.users.SignedInUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.UnsupportedEncodingException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "beacon.registration.invite-only=true",
        "beacon.admin-emails=Admin@Example.test, other-admin@example.test"})
@Transactional
class InviteApiTests extends SignedInApiTests {

    @Autowired MockMvcTester anonymous;
    @Autowired JdbcTemplate jdbc;

    private SignedInUser admin;
    private MockMvcTester asAdmin;

    @BeforeEach
    void admin() {
        admin = accounts.register("admin@example.test", "correct horse battery", "Admin");
        asAdmin = as(admin);
    }

    @Test
    void signingUpNeedsAnInviteCodeWhileInviteOnly() {
        assertThat(anonymous.get().uri("/api/auth/registration").exchange())
                .bodyJson().extractingPath("$.inviteOnly").isEqualTo(true);

        var noCode = register("pat@example.test", null);
        assertThat(noCode).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(noCode).bodyJson().extractingPath("$.errors.inviteCode[0]").asString().contains("invite-only");

        var madeUp = register("pat@example.test", "AAAA-BBBB-CCCC");
        assertThat(madeUp).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(madeUp).bodyJson().extractingPath("$.errors.inviteCode[0]").asString().contains("isn't valid");
        // That no account is left behind is checked by InviteRollbackApiTests, outside a test transaction.
    }

    @Test
    void anInviteCodeWorksOnceAndShowsWhoUsedIt() {
        var created = asAdmin.post().uri("/api/admin/invites").contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\": \" Pat, from LinkedIn \"}").exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        assertThat(created).bodyJson().extractingPath("$.state").isEqualTo("OPEN");
        assertThat(created).bodyJson().extractingPath("$.note").isEqualTo("Pat, from LinkedIn");
        String code = read(created, "$.code");
        assertThat(code).matches("[2-9A-Z]{4}-[2-9A-Z]{4}-[2-9A-Z]{4}");

        // Typed in lower case and without dashes, it still works.
        var signedUp = register("pat@example.test", " " + code.toLowerCase().replace("-", "") + " ");
        assertThat(signedUp).hasStatus(HttpStatus.CREATED);
        assertThat(signedUp).bodyJson().extractingPath("$.admin").isEqualTo(false);

        var again = register("sam@example.test", code);
        assertThat(again).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(again).bodyJson().extractingPath("$.errors.inviteCode").isNotNull();

        var list = asAdmin.get().uri("/api/admin/invites").exchange();
        assertThat(list).bodyJson().extractingPath("$[0].state").isEqualTo("USED");
        assertThat(list).bodyJson().extractingPath("$[0].usedBy").isEqualTo("pat@example.test");
    }

    @Test
    void aFailedSignUpLeavesTheCodeUnused() {
        String code = read(asAdmin.post().uri("/api/admin/invites").contentType(MediaType.APPLICATION_JSON)
                .content("{}").exchange(), "$.code");

        // The email is taken: the sign-up fails, and the code still works for someone else.
        assertThat(register("admin@example.test", code)).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(register("pat@example.test", code)).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void revokedAndExpiredCodesDontWork() {
        String revokedId = read(asAdmin.post().uri("/api/admin/invites").contentType(MediaType.APPLICATION_JSON)
                .content("{}").exchange(), "$.id");
        String revoked = jdbc.queryForObject("select code from invite_codes where id = ?::uuid", String.class, revokedId);
        assertThat(asAdmin.post().uri("/api/admin/invites/{id}/revoke", revokedId).exchange())
                .bodyJson().extractingPath("$.state").isEqualTo("REVOKED");

        String expiredId = read(asAdmin.post().uri("/api/admin/invites").contentType(MediaType.APPLICATION_JSON)
                .content("{\"days\": 1}").exchange(), "$.id");
        jdbc.update("update invite_codes set expires_at = now() - interval '1 minute' where id = ?::uuid", expiredId);
        String expired = jdbc.queryForObject("select code from invite_codes where id = ?::uuid", String.class, expiredId);

        assertThat(register("pat@example.test", revoked)).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(register("pat@example.test", expired)).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(asAdmin.get().uri("/api/admin/invites").exchange())
                .bodyJson().extractingPath("$[*].state").asArray().containsExactlyInAnyOrder("REVOKED", "EXPIRED");
    }

    @Test
    void onlyAdminsSeeInvitesAndTheyAreToldTheyAreAdmins() {
        assertThat(mvc.get().uri("/api/admin/invites").exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.post().uri("/api/admin/invites").contentType(MediaType.APPLICATION_JSON).content("{}").exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/api/auth/me").exchange()).bodyJson().extractingPath("$.admin").isEqualTo(false);
        assertThat(asAdmin.get().uri("/api/auth/me").exchange()).bodyJson().extractingPath("$.admin").isEqualTo(true);
        assertThat(anonymous.get().uri("/api/admin/invites").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private MvcTestResult register(String email, String inviteCode) {
        var code = inviteCode == null ? "null" : "\"" + inviteCode + "\"";
        return anonymous.post().uri("/api/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "correct horse battery", "inviteCode": %s}
                        """.formatted(email, code))
                .exchange();
    }

    private static <T> T read(MvcTestResult result, String path) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), path);
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
