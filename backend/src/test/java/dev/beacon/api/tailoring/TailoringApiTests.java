package dev.beacon.api.tailoring;

import com.jayway.jsonpath.JsonPath;
import dev.beacon.DocumentTestDoubles;
import dev.beacon.DocumentTestDoubles.FakeResumeTailor;
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
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, DocumentTestDoubles.class})
@Transactional
class TailoringApiTests extends SignedInApiTests {

    @Autowired FakeResumeTailor tailor;
    @Autowired ApiKeyService keys;

    private String appId;

    @BeforeEach
    void setUp() {
        tailor.reset();
        keys.save(signedIn.id(), "openai", Map.of("apiKey", "sk-test-users-own"));
        appId = read(mvc.post().uri("/api/applications").contentType(MediaType.APPLICATION_JSON).content("""
                {"companyName": "GHX", "role": "Backend Engineer",
                 "jobDescription": "Java, Spring Boot and Kubernetes."}
                """).exchange(), "$.id");
    }

    @Test
    void suggestsChangesForTheFitsResumeUsingWhatTheFitFound() {
        var resume = uploadResume("Jordan Smith\nJava and Spring at Acme.");
        assertThat(mvc.post().uri("/api/applications/{id}/fit", appId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"resumeDocumentId\": \"" + resume + "\"}").exchange()).hasStatus(HttpStatus.OK);

        var result = suggest("{}");

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.resumeDocumentId").isEqualTo(resume);
        assertThat(result).bodyJson().extractingPath("$.resumeLabel").isEqualTo("backend");
        assertThat(result).bodyJson().extractingPath("$.suggestions[*].kind").asArray().containsExactly("REWORD", "MOVE");
        assertThat(result).bodyJson().extractingPath("$.suggestions[0].example")
                .isEqualTo("Built REST APIs in Spring Boot 3.");
        assertThat(result).bodyJson().extractingPath("$.suggestions[1].example").isNull();
        assertThat(result).bodyJson().extractingPath("$.suggestions[*].state").asArray().containsOnly("OPEN");

        var input = tailor.inputs.getFirst();
        assertThat(input.resume()).contains("Java and Spring at Acme.");
        assertThat(input.jobDescription()).contains("Kubernetes");
        assertThat(input.gaps()).containsExactly("Kubernetes");
        assertThat(input.matched()).containsExactly("Java", "Spring Boot");
        assertThat(mvc.get().uri("/api/me/ai-usage?zone=UTC").exchange())
                .bodyJson().extractingPath("$.byFeature[*].name").asArray().contains("RESUME_TAILORING");
    }

    @Test
    void askingAgainKeepsTheDoneOnesAndReplacesTheRest() {
        uploadResume("Jordan Smith\nJava");
        List<String> ids = read(suggest("{}"), "$.suggestions[*].id");

        var done = mark(ids.get(0), "DONE");
        assertThat(done).hasStatus(HttpStatus.OK);
        assertThat(done).bodyJson().extractingPath("$.state").isEqualTo("DONE");
        assertThat(mark(ids.get(1), "DISMISSED")).hasStatus(HttpStatus.OK);
        assertThat(mark(ids.get(1), "LATER")).hasStatus(HttpStatus.BAD_REQUEST);

        var again = suggest("{}");

        assertThat(again).bodyJson().extractingPath("$.suggestions[*].suggestion").asArray().containsExactly(
                "Say \"Spring Boot\" rather than \"Spring\" (round 1).",
                "Say \"Spring Boot\" rather than \"Spring\" (round 2).",
                "Move the skills section above education.");
        assertThat(again).bodyJson().extractingPath("$.suggestions[*].state").asArray()
                .containsExactly("DONE", "OPEN", "OPEN");
        // Without a fit, the default resume is used and there are no findings to pass on.
        assertThat(tailor.inputs.getLast().gaps()).isEmpty();
    }

    @Test
    void needsAJobDescriptionAResumeAndAnAiKey() {
        var noDescription = read(mvc.post().uri("/api/applications").contentType(MediaType.APPLICATION_JSON)
                .content("{\"companyName\": \"Initech\", \"role\": \"Dev\"}").exchange(), "$.id");
        assertThat(mvc.post().uri("/api/applications/{id}/resume/suggestions", noDescription)
                .contentType(MediaType.APPLICATION_JSON).content("{}").exchange())
                .hasStatus(HttpStatus.valueOf(422))
                .bodyJson().extractingPath("$.detail").asString().contains("job description");

        assertThat(suggest("{}")).hasStatus(HttpStatus.valueOf(422))
                .bodyJson().extractingPath("$.detail").asString().contains("Upload a resume");

        uploadResume("Jordan Smith\nJava");
        keys.delete(signedIn.id(), "openai");
        assertThat(mvc.get().uri("/api/applications/{id}/resume", appId).exchange())
                .bodyJson().extractingPath("$.configured").isEqualTo(false);
        assertThat(suggest("{}")).hasStatus(HttpStatus.valueOf(422))
                .bodyJson().extractingPath("$.detail").asString().contains("API key");
        assertThat(tailor.inputs).isEmpty();
    }

    @Test
    void recordsTheResumeSentAndStatsCountsByIt() {
        var library = uploadResume("Jordan Smith\nJava");
        var tailored = attachResume("Jordan Smith\nJava, tailored for GHX");

        var sent = mvc.put().uri("/api/applications/{id}/resume/sent", appId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"resumeDocumentId\": \"" + tailored + "\"}").exchange();
        assertThat(sent).hasStatus(HttpStatus.OK);
        assertThat(sent).bodyJson().extractingPath("$.sentResumeDocumentId").isEqualTo(tailored);
        assertThat(mvc.get().uri("/api/stats").exchange())
                .bodyJson().extractingPath("$.byResume[*].name").asArray().containsExactly("ghx-tailored.txt");

        mvc.put().uri("/api/applications/{id}/resume/sent", appId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"resumeDocumentId\": \"" + library + "\"}").exchange();
        assertThat(mvc.get().uri("/api/stats").exchange())
                .bodyJson().extractingPath("$.byResume[*].name").asArray().containsExactly("backend");

        assertThat(mvc.put().uri("/api/applications/{id}/resume/sent", appId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"resumeDocumentId\": null}").exchange())
                .bodyJson().extractingPath("$.sentResumeDocumentId").isNull();
        // The application's version is untouched, so an open edit form can still save.
        assertThat(mvc.get().uri("/api/applications/{id}", appId).exchange())
                .bodyJson().extractingPath("$.version").isEqualTo(0);
    }

    @Test
    void anotherUsersApplicationAndResumesStayHidden() {
        uploadResume("Jordan Smith\nJava");
        List<String> ids = read(suggest("{}"), "$.suggestions[*].id");
        var them = as(register());

        assertThat(them.get().uri("/api/applications/{id}/resume", appId).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(them.post().uri("/api/applications/{id}/resume/suggestions", appId)
                .contentType(MediaType.APPLICATION_JSON).content("{}").exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(them.put().uri("/api/applications/{id}/resume/suggestions/{s}", appId, ids.getFirst())
                .contentType(MediaType.APPLICATION_JSON).content("{\"state\": \"DONE\"}").exchange())
                .hasStatus(HttpStatus.NOT_FOUND);

        // Nor can a resume of theirs be recorded as sent with this user's application.
        var theirResume = read(them.post().uri("/api/documents").multipart()
                .file(new MockMultipartFile("file", "theirs.txt", "text/plain", "Pat\nGo".getBytes()))
                .param("kind", "RESUME").exchange(), "$.id");
        assertThat(mvc.put().uri("/api/applications/{id}/resume/sent", appId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"resumeDocumentId\": \"" + theirResume + "\"}").exchange())
                .hasStatus(HttpStatus.valueOf(422));
    }

    private MvcTestResult suggest(String json) {
        return mvc.post().uri("/api/applications/{id}/resume/suggestions", appId)
                .contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private MvcTestResult mark(String suggestionId, String state) {
        return mvc.put().uri("/api/applications/{id}/resume/suggestions/{s}", appId, suggestionId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"state\": \"" + state + "\"}").exchange();
    }

    /** A library resume; the first one becomes the default, labelled from its file name. */
    private String uploadResume(String text) {
        var file = new MockMultipartFile("file", "backend.txt", "text/plain", text.getBytes());
        return read(mvc.post().uri("/api/documents").multipart().file(file).param("kind", "RESUME").exchange(), "$.id");
    }

    private String attachResume(String text) {
        var file = new MockMultipartFile("file", "ghx-tailored.txt", "text/plain", text.getBytes());
        return read(mvc.post().uri("/api/applications/{id}/documents", appId).multipart().file(file)
                .param("kind", "RESUME").exchange(), "$.id");
    }

    private static <T> T read(MvcTestResult result, String path) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), path);
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
