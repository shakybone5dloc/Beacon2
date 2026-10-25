package dev.beacon.api.apikeys;

import dev.beacon.SignedInApiTests;
import dev.beacon.TestcontainersConfiguration;
import dev.beacon.application.apikeys.ApiKeyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ApiKeysApiTests extends SignedInApiTests {

    private static final String SECRET = "sk-test-0123456789abcdef";

    @Autowired ApiKeyService keys;
    @Autowired JdbcTemplate jdbc;

    @Test
    void listsEveryKeyedProviderWithNothingSaved() {
        var result = mvc.get().uri("/api/me/api-keys").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$[*].provider").asArray()
                .containsExactly("himalayas", "usajobs", "adzuna", "openai", "anthropic");
        assertThat(result).bodyJson().extractingPath("$[*].saved").asArray().containsOnly(false);
    }

    @Test
    void savedKeysAreEncryptedAndNeverSentBack() {
        var saved = save(mvc, "openai", """
                {"values": {"apiKey": "  %s  "}}
                """.formatted(SECRET));

        assertThat(saved).hasStatus(HttpStatus.OK);
        assertThat(saved).bodyJson().extractingPath("$.saved").isEqualTo(true);
        assertThat(saved).bodyJson().extractingPath("$.hint").isEqualTo("cdef");
        assertThat(body(saved)).doesNotContain(SECRET);
        assertThat(body(mvc.get().uri("/api/me/api-keys").exchange())).doesNotContain(SECRET);

        var stored = jdbc.queryForObject("select ciphertext from user_api_keys where user_id = ?", byte[].class, signedIn.id());
        assertThat(new String(stored, StandardCharsets.UTF_8)).doesNotContain(SECRET);
        assertThat(keys.credentials(signedIn.id(), "openai")).contains(Map.of("apiKey", SECRET));
    }

    @Test
    void savingAgainReplacesTheKey() {
        save(mvc, "openai", """
                {"values": {"apiKey": "sk-first-key-1111"}}
                """);
        var replaced = save(mvc, "openai", """
                {"values": {"apiKey": "sk-second-key-2222"}}
                """);

        assertThat(replaced).bodyJson().extractingPath("$.hint").isEqualTo("2222");
        assertThat(keys.credentials(signedIn.id(), "openai")).contains(Map.of("apiKey", "sk-second-key-2222"));
    }

    @Test
    void fieldsAreCheckedAgainstTheCatalog() {
        var missingEmail = save(mvc, "usajobs", """
                {"values": {"apiKey": "abc123abc123"}}
                """);
        assertThat(missingEmail).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(missingEmail).bodyJson().extractingPath("$.errors.email").isNotNull();

        var unknownField = save(mvc, "openai", """
                {"values": {"apiKey": "sk-abc", "token": "x"}}
                """);
        assertThat(unknownField).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(unknownField).bodyJson().extractingPath("$.errors.token").isNotNull();

        assertThat(save(mvc, "remotive", """
                {"values": {}}
                """)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(save(mvc, "nope", """
                {"values": {}}
                """)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void usaJobsKeepsTheEmailAlongsideTheKey() {
        var saved = save(mvc, "usajobs", """
                {"values": {"apiKey": "usajobs-key-9876", "email": "me@example.com"}}
                """);

        assertThat(saved).hasStatus(HttpStatus.OK);
        assertThat(saved).bodyJson().extractingPath("$.hint").isEqualTo("9876");
        assertThat(keys.credentials(signedIn.id(), "usajobs"))
                .contains(Map.of("apiKey", "usajobs-key-9876", "email", "me@example.com"));
    }

    @Test
    void deleteRemovesTheKey() {
        save(mvc, "openai", """
                {"values": {"apiKey": "%s"}}
                """.formatted(SECRET));

        assertThat(mvc.delete().uri("/api/me/api-keys/openai").exchange()).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.delete().uri("/api/me/api-keys/openai").exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(keys.credentials(signedIn.id(), "openai")).isEmpty();
    }

    @Test
    void eachUserHasTheirOwnKeys() {
        save(mvc, "openai", """
                {"values": {"apiKey": "%s"}}
                """.formatted(SECRET));
        var other = as(register());

        assertThat(other.get().uri("/api/me/api-keys").exchange())
                .bodyJson().extractingPath("$[*].saved").asArray().containsOnly(false);
        assertThat(other.delete().uri("/api/me/api-keys/openai").exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(keys.credentials(signedIn.id(), "openai")).isPresent();
    }

    @Test
    void theProviderGuideListsEveryProviderWithItsFields() {
        var result = mvc.get().uri("/api/providers").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$[?(@.id == 'remotive')].needsKey").asArray().containsExactly(false);
        assertThat(result).bodyJson().extractingPath("$[?(@.id == 'himalayas')].needsKey").asArray().containsExactly(false);
        assertThat(result).bodyJson().extractingPath("$[?(@.id == 'usajobs')].cost").asArray().containsExactly("FREE");
        assertThat(result).bodyJson().extractingPath("$[?(@.id == 'usajobs')].needsKey").asArray().containsExactly(true);
        assertThat(result).bodyJson().extractingPath("$[?(@.id == 'usajobs')].fields[*].name").asArray()
                .containsExactly("apiKey", "email");
        assertThat(result).bodyJson().extractingPath("$[?(@.id == 'openai')].kind").asArray().containsExactly("AI");
        // Adzuna is turned off by default (BEACON_DISABLED_SOURCES).
        assertThat(result).bodyJson().extractingPath("$[?(@.id == 'adzuna')].turnedOff").asArray().containsExactly(true);
        assertThat(result).bodyJson().extractingPath("$[?(@.id == 'usajobs')].turnedOff").asArray().containsExactly(false);
    }

    private static MvcTestResult save(MockMvcTester as, String provider, String json) {
        return as.put().uri("/api/me/api-keys/{provider}", provider)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }

    private static String body(MvcTestResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }
}
