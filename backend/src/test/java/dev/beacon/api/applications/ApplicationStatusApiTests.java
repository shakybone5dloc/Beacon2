package dev.beacon.api.applications;

import dev.beacon.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ApplicationStatusApiTests {

    @Autowired MockMvcTester mvc;

    @Test
    void allowedMoveReturns200WithBumpedVersion() {
        var id = createApplication();

        var result = patchStatus(id, "APPLIED", 0);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("APPLIED");
        assertThat(result).bodyJson().extractingPath("$.version").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.allowedTransitions").asArray()
                .containsExactly("INTERVIEWING", "REJECTED", "WITHDRAWN");
    }

    @Test
    void staleVersionReturns409() {
        var id = createApplication();

        var result = patchStatus(id, "APPLIED", 5);

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void illegalMoveReturns409() {
        var id = createApplication();

        var result = patchStatus(id, "OFFER", 0);

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.detail").asString().contains("SAVED");
    }

    @Test
    void unknownStatusReturns400() {
        var id = createApplication();

        var result = patchStatus(id, "Ghosted", 0);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errors.status").isNotNull();
    }

    @Test
    void unknownIdReturns404() {
        var randomId = UUID.randomUUID().toString();
        var result = patchStatus(randomId, "APPLIED", 0);

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void lowerCaseStatusReturns200() {
        var id = createApplication();

        var result = patchStatus(id, "applied", 0);

        assertThat(result).hasStatus(HttpStatus.OK);
    }

    // --- helpers ---

    private String createApplication() {
        var result = mvc.post().uri("/api/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"company": "Acme", "role": "Engineer"}
                        """)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);

        var location = result.getResponse().getHeader(HttpHeaders.LOCATION);
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private MvcTestResult patchStatus(String id, String status, long version) {
        return mvc.patch().uri("/api/applications/{id}/status", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status": "%s", "version": %d}
                        """.formatted(status, version))
                .exchange();
    }
}