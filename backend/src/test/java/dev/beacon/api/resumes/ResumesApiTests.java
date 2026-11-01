package dev.beacon.api.resumes;

import com.jayway.jsonpath.JsonPath;
import dev.beacon.DocumentTestDoubles;
import dev.beacon.SignedInApiTests;
import dev.beacon.TestcontainersConfiguration;
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
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ResumesApiTests extends SignedInApiTests {

    @Test
    void aLibraryResumeGetsAProfileAndTheFirstIsTheDefault() {
        upload("Aaron Keiper - Backend.pdf", "application/pdf",
                DocumentTestDoubles.pdfWithText("Aaron Keiper", "Java and Spring Boot, 7 years"), "RESUME");
        upload("Aaron Keiper - Test.pdf", "application/pdf", DocumentTestDoubles.pdfWithText("Test engineering"), "RESUME");
        upload("references.pdf", "application/pdf", DocumentTestDoubles.pdfWithText("References"), "OTHER");

        var resumes = mvc.get().uri("/api/resumes").exchange();

        assertThat(resumes).hasStatus(HttpStatus.OK);
        assertThat(resumes).bodyJson().extractingPath("$[*].label").asArray()
                .containsExactly("Aaron Keiper - Backend", "Aaron Keiper - Test");
        assertThat(resumes).bodyJson().extractingPath("$[*].isDefault").asArray().containsExactly(true, false);
        assertThat(resumes).bodyJson().extractingPath("$[0].textStatus").isEqualTo("READ");
        assertThat(resumes).bodyJson().extractingPath("$[0].filename").isEqualTo("Aaron Keiper - Backend.pdf");
    }

    @Test
    void keepsTheTextItReadFromTheFile() {
        var id = upload("resume.txt", "text/plain", "Aaron Keiper\nJava, Spring Boot, Angular".getBytes(StandardCharsets.UTF_8), "RESUME");

        assertThat(mvc.get().uri("/api/resumes/{id}/text", id).exchange())
                .bodyJson().extractingPath("$.text").isEqualTo("Aaron Keiper\nJava, Spring Boot, Angular");
    }

    @Test
    void aWordResumeIsKeptButMarkedUnreadable() {
        var id = upload("resume.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                new byte[] {80, 75, 3, 4, 1, 2, 3}, "RESUME");

        assertThat(mvc.get().uri("/api/resumes").exchange())
                .bodyJson().extractingPath("$[0].textStatus").isEqualTo("UNREADABLE");
        assertThat(mvc.get().uri("/api/resumes/{id}/text", id).exchange())
                .bodyJson().extractingPath("$.text").isNull();
    }

    @Test
    void makesAnotherResumeTheDefault() {
        var first = upload("backend.pdf", "application/pdf", DocumentTestDoubles.pdfWithText("Backend"), "RESUME");
        var second = upload("test.pdf", "application/pdf", DocumentTestDoubles.pdfWithText("Test"), "RESUME");

        var made = mvc.put().uri("/api/resumes/{id}/default", second).exchange();

        assertThat(made).hasStatus(HttpStatus.OK);
        assertThat(made).bodyJson().extractingPath("$.isDefault").isEqualTo(true);
        var resumes = mvc.get().uri("/api/resumes").exchange();
        assertThat(resumes).bodyJson().extractingPath("$[*].documentId").asArray().containsExactly(second, first);
        assertThat(resumes).bodyJson().extractingPath("$[*].isDefault").asArray().containsExactly(true, false);
    }

    @Test
    void updatesTheLabelAndSummaryWithTheVersionItWasLoadedAt() {
        var id = upload("resume.pdf", "application/pdf", DocumentTestDoubles.pdfWithText("Resume"), "RESUME");

        var updated = update(id, """
                {"label": " Backend Java ", "targetTitles": ["Senior Java Engineer", " ", "Staff Engineer"],
                 "skills": ["Java", "Spring Boot", "Java"], "yearsExperience": 7, "version": 0}
                """);

        assertThat(updated).hasStatus(HttpStatus.OK);
        assertThat(updated).bodyJson().extractingPath("$.label").isEqualTo("Backend Java");
        assertThat(updated).bodyJson().extractingPath("$.targetTitles").asArray()
                .containsExactly("Senior Java Engineer", "Staff Engineer");
        assertThat(updated).bodyJson().extractingPath("$.skills").asArray().containsExactly("Java", "Spring Boot");
        assertThat(updated).bodyJson().extractingPath("$.yearsExperience").isEqualTo(7);

        assertThat(update(id, """
                {"label": "Other", "version": 0}
                """)).hasStatus(HttpStatus.CONFLICT);
        var blank = update(id, """
                {"label": " ", "version": 1}
                """);
        assertThat(blank).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(blank).bodyJson().extractingPath("$.errors.label").isNotNull();
    }

    @Test
    void deletingTheDefaultHandsItToTheNewestRemaining() {
        upload("one.pdf", "application/pdf", DocumentTestDoubles.pdfWithText("One"), "RESUME");
        var middle = upload("two.pdf", "application/pdf", DocumentTestDoubles.pdfWithText("Two"), "RESUME");
        upload("three.pdf", "application/pdf", DocumentTestDoubles.pdfWithText("Three"), "RESUME");
        mvc.put().uri("/api/resumes/{id}/default", middle).exchange();

        assertThat(mvc.delete().uri("/api/documents/{id}", middle).exchange()).hasStatus(HttpStatus.NO_CONTENT);

        var resumes = mvc.get().uri("/api/resumes").exchange();
        assertThat(resumes).bodyJson().extractingPath("$[*].label").asArray().containsExactly("three", "one");
        assertThat(resumes).bodyJson().extractingPath("$[0].isDefault").isEqualTo(true);
        assertThat(mvc.get().uri("/api/resumes/{id}/text", middle).exchange()).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void aResumeAttachedToOneApplicationIsNotInTheLibrary() {
        var app = mvc.post().uri("/api/applications").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"companyName": "Acme", "role": "Engineer"}
                        """).exchange();
        var file = new MockMultipartFile("file", "acme.pdf", "application/pdf", DocumentTestDoubles.pdfWithText("Acme"));
        assertThat(mvc.post().uri("/api/applications/{id}/documents", read(app, "$.id")).multipart().file(file)
                .param("kind", "RESUME").exchange()).hasStatus(HttpStatus.CREATED);

        assertThat(mvc.get().uri("/api/resumes").exchange()).bodyJson().extractingPath("$").asArray().isEmpty();
    }

    @Test
    void anotherUsersResumesCantBeReached() {
        var id = upload("resume.pdf", "application/pdf", DocumentTestDoubles.pdfWithText("Resume"), "RESUME");
        var other = as(register());

        assertThat(other.get().uri("/api/resumes").exchange()).bodyJson().extractingPath("$").asArray().isEmpty();
        assertThat(other.get().uri("/api/resumes/{id}/text", id).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(other.put().uri("/api/resumes/{id}/default", id).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(other.put().uri("/api/resumes/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"label": "Mine now", "version": 0}
                        """).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        // Their first resume is their own default, whatever this user has.
        upload(other, "theirs.pdf");
        assertThat(other.get().uri("/api/resumes").exchange())
                .bodyJson().extractingPath("$[0].isDefault").isEqualTo(true);
    }

    private String upload(String filename, String contentType, byte[] content, String kind) {
        var file = new MockMultipartFile("file", filename, contentType, content);
        var result = mvc.post().uri("/api/documents").multipart().file(file).param("kind", kind).exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return read(result, "$.id");
    }

    private void upload(MockMvcTester as, String filename) {
        var file = new MockMultipartFile("file", filename, "application/pdf", DocumentTestDoubles.pdfWithText("Theirs"));
        assertThat(as.post().uri("/api/documents").multipart().file(file).param("kind", "RESUME").exchange())
                .hasStatus(HttpStatus.CREATED);
    }

    private MvcTestResult update(String id, String json) {
        return mvc.put().uri("/api/resumes/{id}", id).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private static String read(MvcTestResult result, String path) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), path);
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
