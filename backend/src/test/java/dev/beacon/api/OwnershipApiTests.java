package dev.beacon.api;

import com.jayway.jsonpath.JsonPath;
import dev.beacon.DocumentTestDoubles;
import dev.beacon.SignedInApiTests;
import dev.beacon.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.UnsupportedEncodingException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** One user's applications and documents are invisible to everyone else: 404, never 403. */
@SpringBootTest
@Import({TestcontainersConfiguration.class, DocumentTestDoubles.class})
@Transactional
class OwnershipApiTests extends SignedInApiTests {

    private MockMvcTester other;

    @BeforeEach
    void signInSomeoneElse() {
        other = as(register());
    }

    @Test
    void anotherUsersApplicationIsNotFound() {
        var id = createApplication(mvc);

        assertThat(other.get().uri("/api/applications").exchange())
                .bodyJson().extractingPath("$").asArray().isEmpty();
        assertThat(other.get().uri("/api/applications/{id}", id).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(other.get().uri("/api/applications/{id}/history", id).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(other.put().uri("/api/applications/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"companyName": "Hijacked", "role": "Engineer", "version": 0}
                        """)
                .exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(other.patch().uri("/api/applications/{id}/status", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status": "APPLIED", "version": 0}
                        """)
                .exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(other.delete().uri("/api/applications/{id}", id).exchange()).hasStatus(HttpStatus.NOT_FOUND);

        assertThat(mvc.get().uri("/api/applications/{id}", id).exchange())
                .bodyJson().extractingPath("$.company").isEqualTo("Acme");
    }

    @Test
    void anotherUsersDocumentsAreNotFound() {
        var appId = createApplication(mvc);
        var attached = id(mvc.post().uri("/api/applications/{id}/documents", appId).multipart().file(file()).param("kind", "resume").exchange());
        var library = id(mvc.post().uri("/api/documents").multipart().file(file()).param("kind", "resume").exchange());

        assertThat(other.get().uri("/api/applications/{id}/documents", appId).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(other.post().uri("/api/applications/{id}/documents", appId).multipart().file(file()).exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(other.get().uri("/api/applications/{id}/documents/{doc}/content", appId, attached).exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(other.delete().uri("/api/applications/{id}/documents/{doc}", appId, attached).exchange())
                .hasStatus(HttpStatus.NOT_FOUND);

        assertThat(other.get().uri("/api/documents").exchange()).bodyJson().extractingPath("$").asArray().isEmpty();
        assertThat(other.get().uri("/api/documents/{doc}/content", library).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(other.delete().uri("/api/documents/{doc}", library).exchange()).hasStatus(HttpStatus.NOT_FOUND);

        assertThat(mvc.get().uri("/api/documents/{doc}/content", library).exchange()).hasStatus(HttpStatus.OK);
    }

    @Test
    void anotherUsersCompanyIsNotFoundAndCantBeLinked() {
        var appId = createApplication(mvc);
        var companyId = JsonPath.<String>read(body(mvc.get().uri("/api/applications/{id}", appId).exchange()), "$.companyId");

        assertThat(other.get().uri("/api/companies").exchange()).bodyJson().extractingPath("$").asArray().isEmpty();
        assertThat(other.get().uri("/api/companies/{id}", companyId).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(other.get().uri("/api/companies/{id}/applications", companyId).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(other.put().uri("/api/companies/{id}", companyId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Hijacked", "type": "EMPLOYER", "version": 0}
                        """)
                .exchange()).hasStatus(HttpStatus.NOT_FOUND);

        var linked = other.post().uri("/api/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"companyId": "%s", "role": "Engineer"}
                        """.formatted(companyId))
                .exchange();
        assertThat(linked).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(linked).bodyJson().extractingPath("$.errors.companyId").isNotNull();
    }

    private static String createApplication(MockMvcTester as) {
        return id(as.post().uri("/api/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"companyName": "Acme", "role": "Engineer"}
                        """)
                .exchange());
    }

    private static MockMultipartFile file() {
        return new MockMultipartFile("file", "resume.pdf", "application/pdf", DocumentTestDoubles.pdfWithText("Resume"));
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String id(MvcTestResult result) {
        assertThat(result).hasStatus(HttpStatus.CREATED);
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
