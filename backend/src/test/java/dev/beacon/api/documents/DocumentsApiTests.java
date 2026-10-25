package dev.beacon.api.documents;

import com.jayway.jsonpath.JsonPath;
import dev.beacon.DocumentTestDoubles;
import dev.beacon.SignedInApiTests;
import dev.beacon.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, DocumentTestDoubles.class})
@Transactional
class DocumentsApiTests extends SignedInApiTests {

    @Test
    void uploadListDownloadAndDeleteAnApplicationDocument() {
        var appId = createApplication();
        var pdf = DocumentTestDoubles.pdfWithText("Resume");

        var uploaded = upload("/api/applications/" + appId + "/documents", file("resume.pdf", pdf), "resume");
        assertThat(uploaded).hasStatus(HttpStatus.CREATED);
        assertThat(uploaded).bodyJson().extractingPath("$.kind").isEqualTo("RESUME");
        assertThat(uploaded).bodyJson().extractingPath("$.filename").isEqualTo("resume.pdf");
        assertThat(uploaded).bodyJson().extractingPath("$.contentType").isEqualTo("application/pdf");
        assertThat(uploaded).bodyJson().extractingPath("$.sizeBytes").isEqualTo(pdf.length);
        var docId = idFromBody(uploaded);

        var list = mvc.get().uri("/api/applications/{id}/documents", appId).exchange();
        assertThat(list).bodyJson().extractingPath("$[*].id").asArray().containsExactly(docId);

        var download = mvc.get().uri("/api/applications/{id}/documents/{doc}/content", appId, docId).exchange();
        assertThat(download).hasStatus(HttpStatus.OK);
        assertThat(download).hasContentType(MediaType.APPLICATION_PDF);
        assertThat(download.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).startsWith("inline; filename=\"resume.pdf\"");
        assertThat(download).headers().hasValue("X-Content-Type-Options", "nosniff");
        assertThat(download.getResponse().getContentAsByteArray()).isEqualTo(pdf);

        assertThat(mvc.delete().uri("/api/applications/{id}/documents/{doc}", appId, docId).exchange())
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/applications/{id}/documents/{doc}/content", appId, docId).exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void contentTypeComesFromTheExtensionNotTheBrowser() {
        var appId = createApplication();
        var sneaky = new MockMultipartFile("file", "notes.txt", "text/html", "<script>x</script>".getBytes(StandardCharsets.UTF_8));

        var uploaded = upload("/api/applications/" + appId + "/documents", sneaky, "other");

        assertThat(uploaded).bodyJson().extractingPath("$.contentType").isEqualTo("text/plain");
    }

    @Test
    void wordFilesDownloadRatherThanOpenInline() {
        var appId = createApplication();
        var docId = idFromBody(upload("/api/applications/" + appId + "/documents", file("letter.docx", new byte[]{1, 2, 3}), "cover_letter"));

        var download = mvc.get().uri("/api/applications/{id}/documents/{doc}/content", appId, docId).exchange();

        assertThat(download.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment; filename=\"letter.docx\"");
    }

    @Test
    void rejectsUnsupportedTypesAndEmptyFiles() {
        var appId = createApplication();

        var html = upload("/api/applications/" + appId + "/documents", file("page.html", "<p>hi</p>".getBytes()), "other");
        assertThat(html).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(html).bodyJson().extractingPath("$.errors.file").isNotNull();

        var empty = upload("/api/applications/" + appId + "/documents", file("resume.pdf", new byte[0]), "resume");
        assertThat(empty).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsAnUnknownKind() {
        var appId = createApplication();

        var result = upload("/api/applications/" + appId + "/documents", file("a.pdf", new byte[]{1}), "transcript");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errors.kind").isNotNull();
    }

    @Test
    void unknownApplicationReturns404() {
        var result = upload("/api/applications/" + UUID.randomUUID() + "/documents", file("a.pdf", new byte[]{1}), "other");

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void anApplicationsDocumentIsNotReachableFromAnotherApplication() {
        var first = createApplication();
        var second = createApplication();
        var docId = idFromBody(upload("/api/applications/" + first + "/documents", file("a.pdf", new byte[]{1}), "other"));

        assertThat(mvc.get().uri("/api/applications/{id}/documents/{doc}/content", second, docId).exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/api/documents/{doc}/content", docId).exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void libraryDocumentsAreSeparateFromApplications() {
        var appId = createApplication();
        upload("/api/applications/" + appId + "/documents", file("app.pdf", new byte[]{1}), "other");

        var uploaded = upload("/api/documents", file("main-resume.pdf", DocumentTestDoubles.pdfWithText("Me")), "resume");
        assertThat(uploaded).hasStatus(HttpStatus.CREATED);
        assertThat(uploaded).bodyJson().extractingPath("$.applicationId").isNull();
        var docId = idFromBody(uploaded);

        var list = mvc.get().uri("/api/documents").exchange();
        assertThat(list).bodyJson().extractingPath("$[*].filename").asArray().containsExactly("main-resume.pdf");

        assertThat(mvc.get().uri("/api/documents/{doc}/content", docId).exchange()).hasStatus(HttpStatus.OK);
        assertThat(mvc.delete().uri("/api/documents/{doc}", docId).exchange()).hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    void deletingAnApplicationDeletesItsDocuments() {
        var appId = createApplication();
        var docId = idFromBody(upload("/api/applications/" + appId + "/documents", file("a.pdf", new byte[]{1}), "other"));

        mvc.delete().uri("/api/applications/{id}", appId).exchange();

        assertThat(mvc.get().uri("/api/applications/{id}/documents/{doc}/content", appId, docId).exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    // --- helpers ---

    private static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content);
    }

    private MvcTestResult upload(String uri, MockMultipartFile file, String kind) {
        return mvc.post().uri(uri).multipart().file(file).param("kind", kind).exchange();
    }

    private String createApplication() {
        var result = mvc.post().uri("/api/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"company\": \"Acme\", \"role\": \"Engineer\"}")
                .exchange();
        var location = result.getResponse().getHeader(HttpHeaders.LOCATION);
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private static String idFromBody(MvcTestResult result) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
