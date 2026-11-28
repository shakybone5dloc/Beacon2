package dev.beacon.api.applications;

import dev.beacon.SignedInApiTests;
import dev.beacon.TestcontainersConfiguration;
import dev.beacon.domain.applications.ApplicationStatus;
import dev.beacon.domain.applications.StatusChangeSource;
import dev.beacon.infrastructure.persistence.JobApplicationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.Instant;
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
class ApplicationStatusApiTests extends SignedInApiTests {

    @Autowired JobApplicationRepository applications;

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

    @Test
    void historyListsEveryMoveOldestFirst() {
        var id = createApplication();
        patchStatus(id, "APPLIED", 0);
        patchStatus(id, "INTERVIEWING", 1);

        var result = mvc.get().uri("/api/applications/{id}/history", id).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$[*].toStatus").asArray()
                .containsExactly("SAVED", "APPLIED", "INTERVIEWING");
        assertThat(result).bodyJson().extractingPath("$[0].fromStatus").isNull();
        assertThat(result).bodyJson().extractingPath("$[2].fromStatus").isEqualTo("APPLIED");
    }

    @Test
    void historyEntriesCarryIdAndSource() {
        var id = createApplication();
        patchStatus(id, "APPLIED", 0);

        var result = mvc.get().uri("/api/applications/{id}/history", id).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$[*].source").asArray().containsExactly("USER", "USER");
        assertThat(result).bodyJson().extractingPath("$[1].sourceEmailId").isNull();
        assertThat(result).bodyJson().extractingPath("$[1].id").isNotNull();
    }

    @Test
    void timelineStatusEntriesCarrySource() {
        var id = createApplication();
        patchStatus(id, "APPLIED", 0);

        var result = mvc.get().uri("/api/applications/{id}/timeline", id).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$[?(@.kind == 'STATUS_CHANGE')].statusChange.source")
                .asArray().containsExactly("USER", "USER");
    }

    @Test
    void historyForUnknownIdReturns404() {
        var result = mvc.get().uri("/api/applications/{id}/history", UUID.randomUUID()).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void moveResponseIncludesStatusChangedAt() {
        var id = createApplication();

        var result = patchStatus(id, "APPLIED", 0);

        assertThat(result).bodyJson().extractingPath("$.statusChangedAt").isNotNull();
    }

    // --- revert ---

    @Test
    void revertLatestUserMoveRestoresPriorStatusAndRecordsEntry() {
        var id = createApplication();
        patchStatus(id, "APPLIED", 0);
        patchStatus(id, "REJECTED", 1);

        var result = revert(id, latestChangeId(id));

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("APPLIED");
        var history = mvc.get().uri("/api/applications/{id}/history", id).exchange();
        assertThat(history).bodyJson().extractingPath("$[3].toStatus").isEqualTo("APPLIED");
        assertThat(history).bodyJson().extractingPath("$[3].source").isEqualTo("USER");
        assertThat(history).bodyJson().extractingPath("$[3].revertsChangeId").isEqualTo(
                ((Number) historyIds(id).get(2)).intValue());
    }

    @Test
    void revertLatestEmailChangeWorks() {
        var id = createApplication();
        patchStatus(id, "APPLIED", 0);
        var app = applications.findById(UUID.fromString(id)).orElseThrow();
        app.changeStatus(ApplicationStatus.REJECTED, Instant.now().plusSeconds(60), StatusChangeSource.EMAIL, "<m1@x>");
        applications.saveAndFlush(app);

        var result = revert(id, latestChangeId(id));

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("APPLIED");
    }

    @Test
    void revertingTheRevertRedoesTheMove() {
        var id = createApplication();
        patchStatus(id, "APPLIED", 0);
        patchStatus(id, "REJECTED", 1);
        revert(id, latestChangeId(id));

        var result = revert(id, latestChangeId(id));

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("REJECTED");
    }

    @Test
    void revertOfOlderChangeReturns409AndChangesNothing() {
        var id = createApplication();
        patchStatus(id, "APPLIED", 0);
        var applied = latestChangeId(id);
        patchStatus(id, "INTERVIEWING", 1);

        var result = revert(id, applied);

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        var history = mvc.get().uri("/api/applications/{id}/history", id).exchange();
        assertThat(history).bodyJson().extractingPath("$.length()").isEqualTo(3);
    }

    @Test
    void revertOfFirstEntryReturns409() {
        var id = createApplication();

        var result = revert(id, latestChangeId(id));

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void revertOnAnotherUsersApplicationReturns404() {
        var id = createApplication();
        patchStatus(id, "APPLIED", 0);
        var changeId = latestChangeId(id);

        var result = as(register()).post().uri("/api/applications/{id}/history/{changeId}/revert", id, changeId).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void revertWithChangeFromAnotherApplicationReturns404() {
        var first = createApplication();
        var second = createApplication();
        patchStatus(second, "APPLIED", 0);

        var result = revert(first, latestChangeId(second));

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void revertOfUnknownApplicationReturns404() {
        assertThat(revert(UUID.randomUUID().toString(), 1)).hasStatus(HttpStatus.NOT_FOUND);
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

    private MvcTestResult patchStatus(String id, String status, long version) {
        return mvc.patch().uri("/api/applications/{id}/status", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status": "%s", "version": %d}
                        """.formatted(status, version))
                .exchange();
    }

    private MvcTestResult revert(String id, long changeId) {
        return mvc.post().uri("/api/applications/{id}/history/{changeId}/revert", id, changeId).exchange();
    }

    private long latestChangeId(String id) {
        var ids = historyIds(id);
        return ((Number) ids.get(ids.size() - 1)).longValue();
    }

    private java.util.List<Object> historyIds(String id) {
        var history = mvc.get().uri("/api/applications/{id}/history", id).exchange();
        try {
            return com.jayway.jsonpath.JsonPath.read(history.getResponse().getContentAsString(), "$[*].id");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
