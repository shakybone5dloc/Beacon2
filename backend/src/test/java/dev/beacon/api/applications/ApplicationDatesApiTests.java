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
class ApplicationDatesApiTests extends SignedInApiTests {

    @Test
    void createAcceptsAndReturnsDates() {
        var result = create("""
                {"company": "Acme", "role": "Engineer", "applyBy": "2026-11-15", "followUpOn": "2026-11-01"}
                """);

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.applyBy").isEqualTo("2026-11-15");
        assertThat(result).bodyJson().extractingPath("$.followUpOn").isEqualTo("2026-11-01");
    }

    @Test
    void datesAreOptional() {
        var result = create("""
                {"company": "Acme", "role": "Engineer"}
                """);

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.applyBy").isNull();
        assertThat(result).bodyJson().extractingPath("$.followUpOn").isNull();
    }

    @Test
    void malformedDateReturns400() {
        var result = create("""
                {"company": "Acme", "role": "Engineer", "applyBy": "next friday"}
                """);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateChangesAndClearsDates() {
        var id = idOf(create("""
                {"company": "Acme", "role": "Engineer", "applyBy": "2026-11-15", "followUpOn": "2026-11-01"}
                """));

        var result = mvc.put().uri("/api/applications/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"company": "Acme", "role": "Engineer", "applyBy": "2026-11-20", "version": 0}
                        """)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.applyBy").isEqualTo("2026-11-20");
        assertThat(result).bodyJson().extractingPath("$.followUpOn").isNull();
    }

    @Test
    void followUpEndpointSetsTheDateAndBumpsVersion() {
        var id = idOf(create("""
                {"company": "Acme", "role": "Engineer"}
                """));

        var result = putFollowUp(id, """
                {"followUpOn": "2026-11-07", "version": 0}
                """);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.followUpOn").isEqualTo("2026-11-07");
        assertThat(result).bodyJson().extractingPath("$.version").isEqualTo(1);
    }

    @Test
    void followUpEndpointClearsWithNull() {
        var id = idOf(create("""
                {"company": "Acme", "role": "Engineer", "followUpOn": "2026-11-01"}
                """));

        var result = putFollowUp(id, """
                {"followUpOn": null, "version": 0}
                """);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.followUpOn").isNull();
    }

    @Test
    void followUpWithStaleVersionReturns409() {
        var id = idOf(create("""
                {"company": "Acme", "role": "Engineer"}
                """));

        var result = putFollowUp(id, """
                {"followUpOn": "2026-11-07", "version": 4}
                """);

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void followUpWithoutVersionReturns400() {
        var id = idOf(create("""
                {"company": "Acme", "role": "Engineer"}
                """));

        var result = putFollowUp(id, """
                {"followUpOn": "2026-11-07"}
                """);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errors.version").isNotNull();
    }

    @Test
    void followUpForUnknownIdReturns404() {
        var result = putFollowUp(UUID.randomUUID().toString(), """
                {"followUpOn": "2026-11-07", "version": 0}
                """);

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    // --- helpers ---

    private MvcTestResult create(String body) {
        return mvc.post().uri("/api/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private static String idOf(MvcTestResult created) {
        assertThat(created).hasStatus(HttpStatus.CREATED);
        var location = created.getResponse().getHeader(HttpHeaders.LOCATION);
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private MvcTestResult putFollowUp(String id, String body) {
        return mvc.put().uri("/api/applications/{id}/follow-up", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }
}
