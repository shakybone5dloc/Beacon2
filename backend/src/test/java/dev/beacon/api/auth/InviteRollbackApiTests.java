package dev.beacon.api.auth;

import dev.beacon.TestcontainersConfiguration;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * Not in a test transaction, on purpose: a sign-up with a bad invite code must leave no account
 * behind, which only shows when the sign-up's own transaction is the one that rolls back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "beacon.registration.invite-only=true")
class InviteRollbackApiTests {

    @Autowired MockMvcTester mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void aSignUpWithABadCodeLeavesNoAccount() {
        var email = "rollback-" + UUID.randomUUID() + "@example.test";

        var result = mvc.post().uri("/api/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "correct horse battery", "inviteCode": "AAAA-BBBB-CCCC"}
                        """.formatted(email))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(jdbc.queryForObject("select count(*) from users where email = ?", Integer.class, email)).isZero();
    }
}
