package dev.beacon.api.fit;

import com.jayway.jsonpath.JsonPath;
import dev.beacon.DocumentTestDoubles;
import dev.beacon.DocumentTestDoubles.FakeFitScorer;
import dev.beacon.SignedInApiTests;
import dev.beacon.TestcontainersConfiguration;
import dev.beacon.application.apikeys.ApiKeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.UnsupportedEncodingException;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, DocumentTestDoubles.class})
@Transactional
class FitApiTests extends SignedInApiTests {

    private static final String APPLICATION = """
            {"companyName": "Acme", "role": "Java Engineer", "jobDescription": "Build services with Java and Kubernetes."}
            """;

    @Autowired FakeFitScorer scorer;
    @Autowired ApiKeyService keys;

    @BeforeEach
    void setUp() {
        scorer.reset();
        keys.save(signedIn.id(), "openai", Map.of("apiKey", "sk-test-users-own"));
    }

    @Test
    void scoresAnApplicationAgainstALibraryResumeAndShowsItAfterwards() {
        var appId = createApplication(APPLICATION);
        var resumeId = uploadToLibrary("backend.txt", "Jordan Smith\nJava, Spring Boot, 8 years");

        var result = score(appId, resumeId);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.score").isEqualTo(82);
        assertThat(result).bodyJson().extractingPath("$.matchedSkills").asArray().containsExactly("Java", "Spring Boot");
        assertThat(result).bodyJson().extractingPath("$.gaps").asArray().containsExactly("Kubernetes");
        assertThat(result).bodyJson().extractingPath("$.resumeLabel").isEqualTo("backend");
        assertThat(result).bodyJson().extractingPath("$.current").isEqualTo(true);
        var input = scorer.inputs.getFirst();
        assertThat(input.company()).isEqualTo("Acme");
        assertThat(input.role()).isEqualTo("Java Engineer");
        assertThat(input.jobDescription()).contains("Kubernetes");
        assertThat(input.resume()).contains("Spring Boot");
        assertThat(scorer.lastApiKey).isEqualTo("sk-test-users-own");

        var shown = mvc.get().uri("/api/applications/{id}/fit", appId).exchange();
        assertThat(shown).bodyJson().extractingPath("$.fit.score").isEqualTo(82);
        assertThat(shown).bodyJson().extractingPath("$.fit.current").isEqualTo(true);
    }

    @Test
    void anApplicationNeverCheckedHasNoFit() {
        var appId = createApplication(APPLICATION);

        assertThat(mvc.get().uri("/api/applications/{id}/fit", appId).exchange())
                .bodyJson().extractingPath("$.fit").isNull();
        assertThat(mvc.get().uri("/api/applications").exchange())
                .bodyJson().extractingPath("$[0].fitScore").isNull();
    }

    @Test
    void theBoardShowsEachApplicationsLatestScore() {
        var appId = createApplication(APPLICATION);
        createApplication("{\"companyName\": \"Other\", \"role\": \"Engineer\"}");
        score(appId, uploadToLibrary("backend.txt", "Jordan Smith\nJava"));

        var board = mvc.get().uri("/api/applications").exchange();

        // Newest first: the unchecked one, then the checked one.
        assertThat(board).bodyJson().extractingPath("$[0].fitScore").isNull();
        assertThat(board).bodyJson().extractingPath("$[1].fitScore").isEqualTo(82);
        assertThat(mvc.get().uri("/api/applications/{id}", appId).exchange())
                .bodyJson().extractingPath("$.fitScore").isEqualTo(82);
    }

    @Test
    void checkingTheSameInputsAgainReusesTheKeptScore() {
        var appId = createApplication(APPLICATION);
        var resumeId = uploadToLibrary("backend.txt", "Jordan Smith\nJava");

        var first = read(score(appId, resumeId), "$.id");
        var second = read(score(appId, resumeId), "$.id");

        assertThat(second).isEqualTo(first);
        assertThat(scorer.inputs).hasSize(1);
    }

    @Test
    void aChangedDescriptionMakesTheFitOutOfDateUntilCheckedAgain() {
        var appId = createApplication(APPLICATION);
        var resumeId = uploadToLibrary("backend.txt", "Jordan Smith\nJava");
        score(appId, resumeId);

        assertThat(mvc.put().uri("/api/applications/{id}", appId).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"companyName": "Acme", "role": "Java Engineer", "jobDescription": "Now it's a Go role.", "version": 0}
                        """).exchange()).hasStatus(HttpStatus.OK);

        assertThat(mvc.get().uri("/api/applications/{id}/fit", appId).exchange())
                .bodyJson().extractingPath("$.fit.current").isEqualTo(false);
        score(appId, resumeId);
        assertThat(scorer.inputs).hasSize(2);
        assertThat(mvc.get().uri("/api/applications/{id}/fit", appId).exchange())
                .bodyJson().extractingPath("$.fit.current").isEqualTo(true);
    }

    @Test
    void aDeadlineFromThePostingFillsAnEmptyApplyByDate() {
        scorer.applyBy = LocalDate.now().plusDays(20);
        var appId = createApplication(APPLICATION);
        var resumeId = uploadToLibrary("backend.txt", "Jordan Smith\nJava");

        assertThat(score(appId, resumeId)).bodyJson().extractingPath("$.applyBy").isEqualTo(scorer.applyBy.toString());

        assertThat(mvc.get().uri("/api/applications/{id}", appId).exchange())
                .bodyJson().extractingPath("$.applyBy").isEqualTo(scorer.applyBy.toString());
    }

    @Test
    void aDeadlineNeverReplacesOneTheUserSet() {
        scorer.applyBy = LocalDate.now().plusDays(20);
        var mine = LocalDate.now().plusDays(5).toString();
        var appId = createApplication("""
                {"companyName": "Acme", "role": "Java Engineer", "jobDescription": "Build services.", "applyBy": "%s"}
                """.formatted(mine));
        var resumeId = uploadToLibrary("backend.txt", "Jordan Smith\nJava");

        score(appId, resumeId);

        assertThat(mvc.get().uri("/api/applications/{id}", appId).exchange())
                .bodyJson().extractingPath("$.applyBy").isEqualTo(mine);
    }

    @Test
    void scoringNeedsAJobDescriptionAReadableResumeAndAnAiKey() {
        var resumeId = uploadToLibrary("backend.txt", "Jordan Smith\nJava");
        var noDescription = createApplication("{\"companyName\": \"Acme\", \"role\": \"Engineer\"}");
        assertThat(score(noDescription, resumeId)).hasStatus(HttpStatus.valueOf(422))
                .bodyJson().extractingPath("$.detail").asString().contains("job description");

        var appId = createApplication(APPLICATION);
        var word = upload(mvc, "resume.docx", new byte[]{1, 2, 3});
        assertThat(score(appId, word)).hasStatus(HttpStatus.valueOf(422))
                .bodyJson().extractingPath("$.detail").asString().contains("PDF, text and Markdown");

        keys.delete(signedIn.id(), "openai");
        assertThat(score(appId, resumeId)).hasStatus(HttpStatus.valueOf(422))
                .bodyJson().extractingPath("$.detail").asString().contains("under Settings");
        assertThat(scorer.inputs).isEmpty();
        assertThat(mvc.get().uri("/api/fit/status").exchange()).bodyJson().extractingPath("$.configured").isEqualTo(false);
    }

    @Test
    void scoresAJobSearchResultWithoutSavingIt() {
        var resumeId = uploadToLibrary("backend.txt", "Jordan Smith\nJava");

        var result = mvc.post().uri("/api/fit").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"resumeDocumentId": "%s", "title": "Platform Engineer", "company": " ",
                         "description": "Kubernetes and Go."}
                        """.formatted(resumeId)).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.score").isEqualTo(82);
        assertThat(scorer.inputs.getFirst().company()).isNull();
        assertThat(scorer.inputs.getFirst().role()).isEqualTo("Platform Engineer");
        assertThat(mvc.get().uri("/api/applications").exchange()).bodyJson().extractingPath("$").asArray().isEmpty();
    }

    @Test
    void savingAScoredResultAndCheckingItReusesTheScore() {
        var resumeId = uploadToLibrary("backend.txt", "Jordan Smith\nJava");
        mvc.post().uri("/api/fit").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"resumeDocumentId": "%s", "title": "Java Engineer", "company": "Acme",
                         "description": "Build services with Java and Kubernetes."}
                        """.formatted(resumeId)).exchange();
        var appId = createApplication(APPLICATION);

        assertThat(score(appId, resumeId)).hasStatus(HttpStatus.OK);
        assertThat(scorer.inputs).hasSize(1);
    }

    @Test
    void anotherUsersApplicationsAndResumesCantBeScored() {
        var appId = createApplication(APPLICATION);
        var resumeId = uploadToLibrary("backend.txt", "Jordan Smith\nJava");
        score(appId, resumeId);
        var other = register();
        keys.save(other.id(), "openai", Map.of("apiKey", "sk-theirs"));
        var them = as(other);

        assertThat(them.get().uri("/api/applications/{id}/fit", appId).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(them.post().uri("/api/applications/{id}/fit", appId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"resumeDocumentId\": \"" + resumeId + "\"}").exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(them.post().uri("/api/fit").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"resumeDocumentId": "%s", "title": "Java Engineer", "description": "Java."}
                        """.formatted(resumeId)).exchange()).hasStatus(HttpStatus.valueOf(422));
    }

    private MvcTestResult score(String appId, String resumeId) {
        return mvc.post().uri("/api/applications/{id}/fit", appId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"resumeDocumentId\": \"" + resumeId + "\"}").exchange();
    }

    private String createApplication(String body) {
        var result = mvc.post().uri("/api/applications").contentType(MediaType.APPLICATION_JSON).content(body).exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return read(result, "$.id");
    }

    private String uploadToLibrary(String filename, String text) {
        return upload(mvc, filename, text.getBytes());
    }

    private static String upload(MockMvcTester as, String filename, byte[] content) {
        var type = filename.endsWith(".txt") ? "text/plain"
                : "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        var file = new MockMultipartFile("file", filename, type, content);
        var result = as.post().uri("/api/documents").multipart().file(file).param("kind", "RESUME").exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return read(result, "$.id");
    }

    private static String read(MvcTestResult result, String path) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), path);
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
