package dev.beacon.api.applications;

import dev.beacon.SignedInApiTests;
import dev.beacon.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ApplicationEditApiTests extends SignedInApiTests {

    @Test
    void updateReturns200WithNewDetailsAndBumpedVersion() {
        var id = createApplication();

        var result = put(id, """
                {"companyName": " Globex ", "role": "Staff Engineer", "jobUrl": "https://globex.com/jobs/1",
                 "notes": "Referred by Sam", "version": 0}
                """);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.company").isEqualTo("Globex");
        assertThat(result).bodyJson().extractingPath("$.role").isEqualTo("Staff Engineer");
        assertThat(result).bodyJson().extractingPath("$.jobUrl").isEqualTo("https://globex.com/jobs/1");
        assertThat(result).bodyJson().extractingPath("$.notes").isEqualTo("Referred by Sam");
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("SAVED");
        assertThat(result).bodyJson().extractingPath("$.version").isEqualTo(1);
    }

    @Test
    void updateWithStaleVersionReturns409() {
        var id = createApplication();

        var result = put(id, """
                {"companyName": "Globex", "role": "Engineer", "version": 3}
                """);

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void updateWithBlankRoleReturns400WithFieldErrors() {
        var id = createApplication();

        var result = put(id, """
                {"companyName": "Acme", "role": "", "version": 0}
                """);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errors.role").isNotNull();
    }

    @Test
    void updateWithoutVersionReturns400() {
        var id = createApplication();

        var result = put(id, """
                {"companyName": "Globex", "role": "Engineer"}
                """);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errors.version").isNotNull();
    }

    @Test
    void updateUnknownIdReturns404() {
        var result = put(UUID.randomUUID().toString(), """
                {"companyName": "Globex", "role": "Engineer", "version": 0}
                """);

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteReturns204AndApplicationIsGone() {
        var id = createApplication();

        var result = mvc.delete().uri("/api/applications/{id}", id).exchange();

        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/applications/{id}", id).exchange()).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteUnknownIdReturns404() {
        var result = mvc.delete().uri("/api/applications/{id}", UUID.randomUUID()).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    // --- helpers ---

    private String createApplication() {
        var result = mvc.post().uri("/api/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"companyName": "Acme", "role": "Engineer"}
                        """)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);

        var location = result.getResponse().getHeader(HttpHeaders.LOCATION);
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private MvcTestResult put(String id, String body) {
        return mvc.put().uri("/api/applications/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }
}
