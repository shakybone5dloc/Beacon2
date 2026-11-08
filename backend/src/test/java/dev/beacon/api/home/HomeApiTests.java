package dev.beacon.api.home;

import com.jayway.jsonpath.JsonPath;
import dev.beacon.SignedInApiTests;
import dev.beacon.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.UnsupportedEncodingException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class HomeApiTests extends SignedInApiTests {

    @Autowired JdbcTemplate jdbc;

    @Test
    void listsAppliedApplicationsWithNoSentResumeAndSavedOnesWithOpenSuggestions() {
        var applied = create("{\"companyName\": \"GHX\", \"role\": \"Backend Engineer\"}");
        move(applied, "APPLIED", 0);
        var recorded = create("{\"companyName\": \"Acme\", \"role\": \"Dev\"}");
        move(recorded, "APPLIED", 0);
        jdbc.update("update job_applications set sent_resume_id = ? where id = ?", resume(), UUID.fromString(recorded));
        var undisclosed = create("{\"role\": \"Contract Java Dev\"}");
        move(undisclosed, "APPLIED", 0);

        var saved = create("{\"companyName\": \"Initech\", \"role\": \"Platform Engineer\"}");
        suggestion(saved, "OPEN");
        suggestion(saved, "OPEN");
        suggestion(saved, "DONE");
        var savedAllDone = create("{\"companyName\": \"Hooli\", \"role\": \"SRE\"}");
        suggestion(savedAllDone, "DONE");

        var todos = mvc.get().uri("/api/home/todos").exchange();

        assertThat(todos).hasStatus(HttpStatus.OK);
        assertThat(todos).bodyJson().extractingPath("$.missingSentResume[*].employer").asArray()
                .containsExactlyInAnyOrder("GHX", "Undisclosed client");
        assertThat(todos).bodyJson().extractingPath("$.openSuggestions[*].employer").asArray().containsExactly("Initech");
        assertThat(todos).bodyJson().extractingPath("$.openSuggestions[0].count").isEqualTo(2);
        assertThat(todos).bodyJson().extractingPath("$.openSuggestions[0].role").isEqualTo("Platform Engineer");

        // Another user's to-dos are their own.
        assertThat(as(register()).get().uri("/api/home/todos").exchange())
                .bodyJson().extractingPath("$.missingSentResume").asArray().isEmpty();
    }

    private String create(String json) {
        return read(mvc.post().uri("/api/applications").contentType(MediaType.APPLICATION_JSON).content(json).exchange(), "$.id");
    }

    private void move(String id, String status, int version) {
        assertThat(mvc.patch().uri("/api/applications/{id}/status", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"" + status + "\", \"version\": " + version + "}").exchange())
                .hasStatus(HttpStatus.OK);
    }

    private UUID resume() {
        var id = UUID.randomUUID();
        jdbc.update("""
                insert into documents (id, owner_id, kind, filename, content_type, size_bytes, content, created_at)
                values (?, ?, 'RESUME', 'r.txt', 'text/plain', 1, 'x'::bytea, now())
                """, id, signedIn.id());
        return id;
    }

    private void suggestion(String applicationId, String state) {
        jdbc.update("""
                insert into resume_suggestions (id, owner_id, application_id, position, kind, section, suggestion,
                    reason, state, model, created_at, updated_at)
                values (?, ?, ?, 0, 'ADD', 'Skills', 'Add Kubernetes.', 'Required.', ?, 'm', now(), now())
                """, UUID.randomUUID(), signedIn.id(), UUID.fromString(applicationId), state);
    }

    private static <T> T read(MvcTestResult result, String path) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), path);
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
