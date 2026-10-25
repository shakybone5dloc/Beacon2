package dev.beacon.api.documents;

import com.jayway.jsonpath.JsonPath;
import dev.beacon.DocumentTestDoubles;
import dev.beacon.DocumentTestDoubles.FakeCoverLetterWriter;
import dev.beacon.DocumentTestDoubles.FakePdfPrinter;
import dev.beacon.SignedInApiTests;
import dev.beacon.TestcontainersConfiguration;
import dev.beacon.application.apikeys.ApiKeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.net.URI;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, DocumentTestDoubles.class})
@Transactional
class PostingCaptureAndCoverLetterApiTests extends SignedInApiTests {

    // A public IP literal, so the private-address check passes without a DNS lookup.
    private static final String JOB_URL = "https://1.1.1.1/jobs/42";

    @Autowired FakePdfPrinter printer;
    @Autowired FakeCoverLetterWriter writer;
    @Autowired ApiKeyService keys;

    @BeforeEach
    void resetFakes() {
        printer.reset();
        writer.lastInput = null;
        writer.lastApiKey = null;
        keys.save(signedIn.id(), "openai", Map.of("apiKey", "sk-test-users-own"));
    }

    // --- posting capture ---

    @Test
    void capturePrintsTheJobUrlAndKeepsItsTextAsTheDescription() {
        var appId = createApplication("{\"company\": \"Acme Corp\", \"role\": \"Engineer\", \"jobUrl\": \"" + JOB_URL + "\"}");

        var result = capture(appId);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.document.kind").isEqualTo("POSTING");
        assertThat(result).bodyJson().extractingPath("$.document.filename").asString().startsWith("posting-acme-corp-");
        assertThat(result).bodyJson().extractingPath("$.descriptionSaved").isEqualTo(true);
        assertThat(printer.printedUrls).containsExactly(URI.create(JOB_URL));

        var app = mvc.get().uri("/api/applications/{id}", appId).exchange();
        assertThat(app).bodyJson().extractingPath("$.jobDescription").asString()
                .contains("Senior Java Engineer").contains("Build services with Spring Boot.");
    }

    @Test
    void captureNeverOverwritesAnExistingDescription() {
        var appId = createApplication("{\"company\": \"Acme\", \"role\": \"Engineer\", \"jobUrl\": \"" + JOB_URL
                + "\", \"jobDescription\": \"My own notes\"}");

        assertThat(capture(appId)).bodyJson().extractingPath("$.descriptionSaved").isEqualTo(false);

        assertThat(mvc.get().uri("/api/applications/{id}", appId).exchange())
                .bodyJson().extractingPath("$.jobDescription").isEqualTo("My own notes");
    }

    @Test
    void aBotCheckPageIsReportedAndNotSaved() {
        var appId = createApplication("{\"company\": \"Acme\", \"role\": \"Engineer\", \"jobUrl\": \"" + JOB_URL + "\"}");
        printer.pageLines = new String[]{"himalayas.app", "Performing security verification",
                "Ray ID: 9f1c2b", "Performance and Security by Cloudflare"};

        var result = capture(appId);

        assertThat(result).hasStatus(HttpStatus.BAD_GATEWAY);
        assertThat(result).bodyJson().extractingPath("$.detail").asString().contains("bot check");
        assertThat(mvc.get().uri("/api/applications/{id}/documents", appId).exchange())
                .bodyJson().extractingPath("$").asArray().isEmpty();
        assertThat(mvc.get().uri("/api/applications/{id}", appId).exchange())
                .bodyJson().extractingPath("$.jobDescription").isNull();
    }

    @Test
    void aPageWithBarelyAnyTextIsKeptButNotUsedAsTheDescription() {
        var appId = createApplication("{\"company\": \"Acme\", \"role\": \"Engineer\", \"jobUrl\": \"" + JOB_URL + "\"}");
        printer.pageLines = new String[]{"Loading..."};

        var result = capture(appId);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.descriptionSaved").isEqualTo(false);
        assertThat(mvc.get().uri("/api/applications/{id}", appId).exchange())
                .bodyJson().extractingPath("$.jobDescription").isNull();
    }

    @Test
    void captureNeedsAJobUrl() {
        var appId = createApplication("{\"company\": \"Acme\", \"role\": \"Engineer\"}");

        var result = capture(appId);

        assertThat(result).hasStatus(HttpStatus.valueOf(422));
        assertThat(result).bodyJson().extractingPath("$.detail").asString().contains("URL");
        assertThat(printer.printedUrls).isEmpty();
    }

    @Test
    void captureRefusesPrivateNetworkAddresses() {
        var appId = createApplication("{\"company\": \"Acme\", \"role\": \"Engineer\", \"jobUrl\": \"http://127.0.0.1:8080/admin\"}");

        var result = capture(appId);

        assertThat(result).hasStatus(HttpStatus.valueOf(422));
        assertThat(printer.printedUrls).isEmpty();
    }

    @Test
    void aBrowserFailureIsA502() {
        var appId = createApplication("{\"company\": \"Acme\", \"role\": \"Engineer\", \"jobUrl\": \"" + JOB_URL + "\"}");
        printer.failWith = "The page took too long to load.";

        var result = capture(appId);

        assertThat(result).hasStatus(HttpStatus.BAD_GATEWAY);
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("The page took too long to load.");
    }

    @Test
    void captureForUnknownApplicationReturns404() {
        assertThat(capture(UUID.randomUUID().toString())).hasStatus(HttpStatus.NOT_FOUND);
    }

    // --- posting summary ---

    @Test
    void postingSummaryPrintsBeaconsOwnCopyOfThePosting() {
        var appId = createApplication("{\"company\": \"Acme\", \"role\": \"Java Engineer\", \"salaryMin\": 140000, "
                + "\"salaryCurrency\": \"USD\", \"jobDescription\": \"Build services.\"}");

        var result = mvc.post().uri("/api/applications/{id}/documents/posting-summary", appId).exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.kind").isEqualTo("POSTING");
        assertThat(result).bodyJson().extractingPath("$.filename").asString().startsWith("posting-acme-summary-");
        assertThat(printer.printedUrls).isEmpty();
        assertThat(printer.printedHtml).singleElement().satisfies(html -> {
            assertThat(html).contains("<h1>Java Engineer</h1>");
            assertThat(html).contains("$140,000");
            assertThat(html).contains("<p>Build services.</p>");
        });
    }

    @Test
    void postingSummaryForUnknownApplicationReturns404() {
        assertThat(mvc.post().uri("/api/applications/{id}/documents/posting-summary", UUID.randomUUID()).exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    // --- cover letters ---

    @Test
    void statusReportsWhetherThisUserHasAnAiKey() {
        assertThat(mvc.get().uri("/api/cover-letters/status").exchange())
                .bodyJson().extractingPath("$.configured").isEqualTo(true);
        assertThat(as(register()).get().uri("/api/cover-letters/status").exchange())
                .bodyJson().extractingPath("$.configured").isEqualTo(false);
    }

    @Test
    void draftWithoutAnAiKeyPointsToSettings() {
        keys.delete(signedIn.id(), "openai");
        var appId = createApplication("{\"company\": \"Acme\", \"role\": \"Engineer\", \"jobDescription\": \"Build services.\"}");
        var resumeId = uploadToLibrary("resume.txt", "Jordan Smith".getBytes());

        var result = draft(appId, resumeId, null);

        assertThat(result).hasStatus(HttpStatus.valueOf(422));
        assertThat(result).bodyJson().extractingPath("$.detail").asString().contains("under Settings");
        assertThat(writer.lastInput).isNull();
    }

    @Test
    void draftUsesTheJobDescriptionAndALibraryResume() {
        var appId = createApplication("{\"company\": \"Acme\", \"role\": \"Engineer\", \"jobDescription\": \"Build services.\"}");
        var resumeId = uploadToLibrary("resume.pdf", DocumentTestDoubles.pdfWithText("Jordan Smith", "Java developer, 8 years"));

        var result = draft(appId, resumeId, "Mention Denver");

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.text").asString().contains("Acme");
        assertThat(writer.lastInput.jobDescription()).isEqualTo("Build services.");
        assertThat(writer.lastInput.resume()).contains("Jordan Smith").contains("Java developer, 8 years");
        assertThat(writer.lastInput.instructions()).isEqualTo("Mention Denver");
        assertThat(writer.lastApiKey).isEqualTo("sk-test-users-own");
    }

    @Test
    void draftNeedsAJobDescription() {
        var appId = createApplication("{\"company\": \"Acme\", \"role\": \"Engineer\"}");
        var resumeId = uploadToLibrary("resume.txt", "Jordan Smith".getBytes());

        var result = draft(appId, resumeId, null);

        assertThat(result).hasStatus(HttpStatus.valueOf(422));
        assertThat(result).bodyJson().extractingPath("$.detail").asString().contains("job description");
    }

    @Test
    void draftExplainsWhenTheResumeCannotBeRead() {
        var appId = createApplication("{\"company\": \"Acme\", \"role\": \"Engineer\", \"jobDescription\": \"Build services.\"}");
        var resumeId = uploadToLibrary("resume.docx", new byte[]{1, 2, 3});

        var result = draft(appId, resumeId, null);

        assertThat(result).hasStatus(HttpStatus.valueOf(422));
        assertThat(result).bodyJson().extractingPath("$.detail").asString().contains("PDF, text and Markdown");
    }

    @Test
    void saveRendersTheLetterAsAPdfCoverLetter() {
        var appId = createApplication("{\"company\": \"Acme\", \"role\": \"Engineer\"}");

        var result = mvc.post().uri("/api/applications/{id}/cover-letter", appId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\": \"Dear Hiring Team,\\n\\nI <3 Java.\\n\\nJordan\"}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.kind").isEqualTo("COVER_LETTER");
        assertThat(result).bodyJson().extractingPath("$.contentType").isEqualTo("application/pdf");
        assertThat(printer.printedHtml).singleElement().satisfies(html -> {
            assertThat(html).contains("<p>Dear Hiring Team,</p>");
            assertThat(html).contains("I &lt;3 Java.");
        });
    }

    // --- helpers ---

    private MvcTestResult capture(String appId) {
        return mvc.post().uri("/api/applications/{id}/documents/posting-capture", appId).exchange();
    }

    private MvcTestResult draft(String appId, String resumeId, String instructions) {
        var body = instructions == null
                ? "{\"resumeDocumentId\": \"" + resumeId + "\"}"
                : "{\"resumeDocumentId\": \"" + resumeId + "\", \"instructions\": \"" + instructions + "\"}";
        return mvc.post().uri("/api/applications/{id}/cover-letter/draft", appId)
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    private String createApplication(String body) {
        var result = mvc.post().uri("/api/applications").contentType(MediaType.APPLICATION_JSON).content(body).exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        var location = result.getResponse().getHeader(HttpHeaders.LOCATION);
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private String uploadToLibrary(String filename, byte[] content) {
        var result = mvc.post().uri("/api/documents").multipart()
                .file(new MockMultipartFile("file", filename, "application/octet-stream", content))
                .param("kind", "resume")
                .exchange();
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
