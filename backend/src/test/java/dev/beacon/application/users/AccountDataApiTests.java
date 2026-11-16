package dev.beacon.application.users;

import com.jayway.jsonpath.JsonPath;
import dev.beacon.DocumentTestDoubles;
import dev.beacon.SignedInApiTests;
import dev.beacon.TestcontainersConfiguration;
import dev.beacon.application.apikeys.ApiKeyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, DocumentTestDoubles.class})
@Transactional
class AccountDataApiTests extends SignedInApiTests {

    @Autowired JdbcTemplate jdbc;
    @Autowired ApiKeyService keys;
    @Autowired AccountData data;
    @Autowired jakarta.persistence.EntityManager entities;

    /** A safety net: a table added later must be exported (and so checked for deletion) or listed as not the user's. */
    @Test
    void everyTableIsExportedOrDeliberatelyLeftOut() {
        var tables = new HashSet<>(jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public' and table_type = 'BASE TABLE'",
                String.class));
        tables.removeAll(AccountData.EXPORTS.keySet());
        tables.removeAll(AccountData.NOT_EXPORTED);

        assertThat(tables).as("tables neither exported nor listed in NOT_EXPORTED").isEmpty();
    }

    @Test
    void exportsEverythingTheUserHasButNoSecrets() throws IOException {
        var appId = fill(signedIn);
        // Someone else's data stays out.
        fill(register());

        // Over HTTP: a zip download. (It's written on another thread, which can't see this test's
        // uncommitted data, so its contents are checked by calling the export directly below.)
        var result = mvc.get().uri("/api/me/export").exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).headers().hasValue("Content-Type", "application/zip");
        assertThat(result).headers().hasValue("Cache-Control", "no-store");
        assertThat(result.getResponse().getHeader("Content-Disposition")).contains("attachment").contains("beacon-export-");
        assertThat(unzip(result.getResponse().getContentAsByteArray())).containsKey("README.txt");

        var out = new ByteArrayOutputStream();
        data.export(signedIn.id(), out);
        var files = unzip(out.toByteArray());

        assertThat(files).containsKeys("README.txt", "data/job_applications.json", "data/contacts.json",
                "data/interviews.json", "data/stories.json", "data/study_cards.json", "data/study_reviews.json");
        assertThat((Object) JsonPath.read(files.get("data/job_applications.json"), "$[*].id")).asList().containsExactly(appId);
        assertThat((Object) JsonPath.read(files.get("data/users.json"), "$[0].email")).isEqualTo(signedIn.email());
        assertThat(files.get("data/users.json")).doesNotContain("password");
        assertThat((Object) JsonPath.read(files.get("data/user_api_keys.json"), "$[0].provider")).isEqualTo("openai");
        assertThat(files.get("data/user_api_keys.json")).doesNotContain("ciphertext").doesNotContain("sk-test");
        assertThat((Object) JsonPath.read(files.get("data/ai_usage.json"), "$.length()")).isEqualTo(1);
        // The file itself, under documents/, matching the listing.
        String docId = JsonPath.read(files.get("data/documents.json"), "$[0].id");
        assertThat(files).containsKey("documents/" + docId + "-My_resume_2026.pdf");
        assertThat(files.get("documents/" + docId + "-My_resume_2026.pdf")).startsWith("%PDF");
    }

    @Test
    void deletingTheAccountNeedsThePasswordAndRemovesEverything() throws IOException {
        fill(signedIn);
        var other = register();
        var othersApp = fill(other);

        var wrong = mvc.method(org.springframework.http.HttpMethod.DELETE).uri("/api/me")
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\": \"not my password\"}").exchange();
        assertThat(wrong).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(wrong).bodyJson().extractingPath("$.errors.password").isNotNull();
        assertThat(jdbc.queryForObject("select count(*) from users where id = ?", Integer.class, signedIn.id())).isOne();

        var deleted = mvc.method(org.springframework.http.HttpMethod.DELETE).uri("/api/me")
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\": \"correct horse battery\"}").exchange();

        assertThat(deleted).hasStatus(HttpStatus.NO_CONTENT);
        // Every kind of data the export covers is gone for this user...
        var out = new ByteArrayOutputStream();
        data.export(signedIn.id(), out);
        unzip(out.toByteArray()).forEach((name, content) -> {
            if (name.startsWith("data/")) assertThat(content).as(name).isEqualTo("[]");
        });
        assertThat(unzip(out.toByteArray()).keySet()).noneMatch(name -> name.startsWith("documents/"));
        // ...and nobody else's was touched.
        assertThat(jdbc.queryForObject("select count(*) from job_applications where id = ?::uuid", Integer.class, othersApp))
                .isOne();
        assertThat(jdbc.queryForObject("select count(*) from ai_usage where owner_id = ?", Integer.class, other.id()))
                .isOne();
    }

    /** One of nearly everything, for this user; returns the application's id. */
    private String fill(SignedInUser user) throws UnsupportedEncodingException {
        var ownerId = user.id();
        // Saved through JPA; flushed so SQL (the AI usage row below, and the export) sees it.
        entities.flush();
        var as = as(user);
        keys.save(ownerId, "openai", Map.of("apiKey", "sk-test-secret-value"));
        String appId = read(as.post().uri("/api/applications").contentType(MediaType.APPLICATION_JSON)
                .content("{\"companyName\": \"Acme\", \"role\": \"Engineer\"}").exchange(), "$.id");
        String personId = read(as.post().uri("/api/people").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Pat Lee\", \"role\": \"RECRUITER\"}").exchange(), "$.id");
        as.post().uri("/api/contacts").contentType(MediaType.APPLICATION_JSON).content("""
                {"personId": "%s", "applicationId": "%s", "channel": "EMAIL", "direction": "INBOUND",
                 "occurredAt": "2026-11-01T12:00:00Z", "body": "Hello"}
                """.formatted(personId, appId)).exchange();
        as.post().uri("/api/applications/{id}/interviews", appId).contentType(MediaType.APPLICATION_JSON).content("""
                {"localStart": "2099-03-10T14:00:00", "timeZone": "UTC", "durationMinutes": 45, "type": "TECHNICAL"}
                """).exchange();
        var file = new MockMultipartFile("file", "My resume (2026).pdf", "application/pdf",
                DocumentTestDoubles.pdfWithText("Resume"));
        assertThat(as.post().uri("/api/applications/{id}/documents", appId).multipart().file(file)
                .param("kind", "resume").exchange()).hasStatus(HttpStatus.CREATED);
        as.post().uri("/api/stories").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"Legacy to cloud\", \"tags\": []}").exchange();
        String deckId = read(as.post().uri("/api/study/decks").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"OAuth\"}").exchange(), "$.id");
        String cardId = read(as.post().uri("/api/study/decks/{id}/cards", deckId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\": \"What is PKCE?\", \"answer\": \"Proof key.\"}").exchange(), "$.id");
        as.post().uri("/api/study/cards/{id}/review", cardId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"grade\": \"GOOD\"}").exchange();
        jdbc.update("""
                insert into ai_usage (id, owner_id, feature, provider, model, input_tokens, output_tokens, created_at)
                values (?, ?, 'COVER_LETTER', 'openai', 'm', 10, 5, now())
                """, UUID.randomUUID(), ownerId);
        entities.flush();
        return appId;
    }

    private static Map<String, String> unzip(byte[] zip) throws IOException {
        var files = new HashMap<String, String>();
        try (var in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            for (var entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                files.put(entry.getName(), new String(in.readAllBytes(), StandardCharsets.ISO_8859_1));
            }
        }
        return files;
    }

    private static <T> T read(MvcTestResult result, String path) throws UnsupportedEncodingException {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }
}
