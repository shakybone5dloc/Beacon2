package dev.beacon.api.briefs;

import com.jayway.jsonpath.JsonPath;
import dev.beacon.DocumentTestDoubles;
import dev.beacon.DocumentTestDoubles.FakeBriefWriter;
import dev.beacon.SignedInApiTests;
import dev.beacon.TestcontainersConfiguration;
import dev.beacon.application.apikeys.ApiKeyService;
import dev.beacon.domain.briefs.SectionKind;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, DocumentTestDoubles.class})
@Transactional
class BriefApiTests extends SignedInApiTests {

    private static final String APPLICATION = """
            {"companyName": "GHX", "role": "Senior Java Engineer",
             "jobDescription": "Build supply chain services with Java, Spring and Kafka."}
            """;

    @Autowired FakeBriefWriter writer;
    @Autowired ApiKeyService keys;

    @BeforeEach
    void setUp() {
        writer.reset();
        keys.save(signedIn.id(), "openai", Map.of("apiKey", "sk-test-users-own"));
    }

    @Test
    void listsEverySectionInReadingOrderWithTheChecklistTemplate() {
        var appId = createApplication(APPLICATION);

        var brief = get(appId);

        assertThat(brief).hasStatus(HttpStatus.OK);
        assertThat(brief).bodyJson().extractingPath("$.sections[*].kind").asArray().containsExactly(
                "PITCH", "CHECKLIST", "COMPANY", "GAP", "STORIES", "QUESTIONS", "REFRESHER", "PRACTICE",
                "QUESTIONS_TO_ASK");
        assertThat(brief).bodyJson().extractingPath("$.sections[1].content").asString().contains("Reread this brief");
        assertThat(brief).bodyJson().extractingPath("$.sections[0].content").isNull();
        assertThat(brief).bodyJson().extractingPath("$.sections[0].writable").isEqualTo(true);
        assertThat(brief).bodyJson().extractingPath("$.sections[2].writable").isEqualTo(false);
        assertThat(brief).bodyJson().extractingPath("$.configured").isEqualTo(true);
        assertThat(brief).bodyJson().extractingPath("$.resumeDocumentId").isNull();
    }

    @Test
    void writesASectionFromTheApplicationResumePeopleAndRounds() {
        var appId = createApplication(APPLICATION);
        var resumeId = uploadResume("backend.txt", "Jordan Smith\nJava, Spring Boot, Kafka. 8 years.");
        var sam = createPerson("Sam Ortiz", "INTERVIEWER");
        var screen = schedule(appId, "2026-10-01T15:00:00", sam, "Asked about Kafka consumer groups.");

        var written = write(appId, "PITCH", null, false);

        assertThat(written).hasStatus(HttpStatus.OK);
        assertThat(written).bodyJson().extractingPath("$.content")
                .isEqualTo("Draft of The role and your pitch for Senior Java Engineer.");
        assertThat(written).bodyJson().extractingPath("$.model").isEqualTo("fake-model");
        assertThat(written).bodyJson().extractingPath("$.edited").isEqualTo(false);
        var input = writer.inputs.getFirst();
        assertThat(input.section()).isEqualTo(SectionKind.PITCH);
        assertThat(input.company()).isEqualTo("GHX");
        assertThat(input.jobDescription()).contains("Kafka");
        assertThat(input.resume()).contains("Spring Boot");
        assertThat(input.people()).singleElement().satisfies(p -> {
            assertThat(p.name()).isEqualTo("Sam Ortiz");
            assertThat(p.role()).isEqualTo("Interviewer");
        });
        assertThat(input.rounds()).singleElement().satisfies(r -> {
            assertThat(r.type()).isEqualTo("Phone screen");
            assertThat(r.when()).isEqualTo("Thu Oct 1, 2026 3:00 PM (UTC)");
            assertThat(r.interviewers()).containsExactly("Sam Ortiz");
            assertThat(r.debriefNotes()).isEqualTo("Asked about Kafka consumer groups.");
        });

        var brief = get(appId);
        assertThat(brief).bodyJson().extractingPath("$.resumeDocumentId").isEqualTo(resumeId);
        assertThat(brief).bodyJson().extractingPath("$.resumeLabel").isEqualTo("backend");
        assertThat(brief).bodyJson().extractingPath("$.sections[0].content").asString().startsWith("Draft of");
        // The past round gets a place for its update, before the questions to ask.
        assertThat(brief).bodyJson().extractingPath("$.sections[8].kind").isEqualTo("ROUND");
        assertThat(brief).bodyJson().extractingPath("$.sections[8].interviewId").isEqualTo(screen);
    }

    @Test
    void aRoundUpdateIsWrittenFromThatRoundsDebriefNotes() {
        var appId = createApplication(APPLICATION);
        uploadResume("backend.txt", "Jordan Smith\nJava");
        var noNotes = schedule(appId, "2026-10-01T15:00:00", null, null);
        var withNotes = schedule(appId, "2026-10-08T15:00:00", null, "Panel next, with the architect.");

        assertThat(write(appId, "ROUND", null, false)).hasStatus(HttpStatus.valueOf(422));
        assertThat(write(appId, "ROUND", noNotes, false)).hasStatus(HttpStatus.valueOf(422))
                .bodyJson().extractingPath("$.detail").asString().contains("debrief notes");
        assertThat(write(appId, "ROUND", UUID.randomUUID().toString(), false)).hasStatus(HttpStatus.NOT_FOUND);

        assertThat(write(appId, "ROUND", withNotes, false)).hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.interviewId").isEqualTo(withNotes);
        assertThat(writer.inputs.getLast().roundId()).hasToString(withNotes);
    }

    @Test
    void neverRewritesTheUsersEditsWithoutBeingTold() {
        var appId = createApplication(APPLICATION);
        uploadResume("backend.txt", "Jordan Smith\nJava");
        Integer version = read(write(appId, "GAP", null, false), "$.version");

        var edited = edit(appId, "GAP", "  | They want | You have |\\n|---|---|  ", version);
        assertThat(edited).hasStatus(HttpStatus.OK);
        assertThat(edited).bodyJson().extractingPath("$.edited").isEqualTo(true);
        assertThat(edited).bodyJson().extractingPath("$.content").isEqualTo("| They want | You have |\n|---|---|");

        assertThat(write(appId, "GAP", null, false)).hasStatus(HttpStatus.CONFLICT);
        assertThat(writer.inputs).hasSize(1);
        var replaced = write(appId, "GAP", null, true);
        assertThat(replaced).hasStatus(HttpStatus.OK);
        assertThat(replaced).bodyJson().extractingPath("$.edited").isEqualTo(false);

        // An edit made from an old copy of the section is turned away.
        assertThat(edit(appId, "GAP", "stale", version)).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void theUsersOwnSectionsAreEditedNotWritten() {
        var appId = createApplication(APPLICATION);
        uploadResume("backend.txt", "Jordan Smith\nJava");

        assertThat(write(appId, "COMPANY", null, false)).hasStatus(HttpStatus.valueOf(422));
        var saved = edit(appId, "COMPANY", "Hospital supply chain network, 2,000+ employees.", null);
        assertThat(saved).hasStatus(HttpStatus.OK);
        assertThat(get(appId)).bodyJson().extractingPath("$.sections[2].content")
                .isEqualTo("Hospital supply chain network, 2,000+ employees.");
        assertThat(writer.inputs).isEmpty();
    }

    @Test
    void writesFromTheResumePickedForTheBrief() {
        var appId = createApplication(APPLICATION);
        uploadResume("backend.txt", "Backend resume text");
        var platform = uploadResume("platform.txt", "Platform resume text");

        var chosen = mvc.put().uri("/api/applications/{id}/brief/resume", appId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"resumeDocumentId\": \"" + platform + "\"}").exchange();
        assertThat(chosen).hasStatus(HttpStatus.OK);
        assertThat(chosen).bodyJson().extractingPath("$.resumeDocumentId").isEqualTo(platform);

        write(appId, "STORIES", null, false);
        assertThat(writer.inputs.getFirst().resume()).isEqualTo("Platform resume text");
    }

    @Test
    void explainsWhatsMissingBeforeCallingTheAi() {
        var noDescription = createApplication("""
                {"companyName": "GHX", "role": "Engineer"}
                """);
        assertThat(write(noDescription, "PITCH", null, false)).hasStatus(HttpStatus.valueOf(422))
                .bodyJson().extractingPath("$.detail").asString().contains("job description");

        var appId = createApplication(APPLICATION);
        assertThat(write(appId, "PITCH", null, false)).hasStatus(HttpStatus.valueOf(422))
                .bodyJson().extractingPath("$.detail").asString().contains("resume");

        uploadResume("backend.txt", "Jordan Smith\nJava");
        keys.delete(signedIn.id(), "openai");
        assertThat(get(appId)).bodyJson().extractingPath("$.configured").isEqualTo(false);
        assertThat(write(appId, "PITCH", null, false)).hasStatus(HttpStatus.valueOf(422))
                .bodyJson().extractingPath("$.detail").asString().contains("API key");
        assertThat(writer.inputs).isEmpty();
    }

    @Test
    void anAiFailureSaysWhatHappenedAndIsCountedAsUsage() {
        var appId = createApplication(APPLICATION);
        uploadResume("backend.txt", "Jordan Smith\nJava");
        writer.failWith = "The AI service is busy right now. Try again in a minute.";

        assertThat(write(appId, "QUESTIONS", null, false)).hasStatus(HttpStatus.BAD_GATEWAY)
                .bodyJson().extractingPath("$.detail").asString().contains("busy");
        assertThat(get(appId)).bodyJson().extractingPath("$.sections[5].content").isNull();
        assertThat(mvc.get().uri("/api/me/ai-usage?zone=UTC").exchange())
                .bodyJson().extractingPath("$.byFeature[*].name").asArray().contains("INTERVIEW_BRIEF");
    }

    @Test
    void anotherUsersBriefStaysHidden() {
        var appId = createApplication(APPLICATION);
        uploadResume("backend.txt", "Jordan Smith\nJava");
        write(appId, "PITCH", null, false);
        var other = register();
        keys.save(other.id(), "openai", Map.of("apiKey", "sk-theirs"));
        var them = as(other);

        assertThat(them.get().uri("/api/applications/{id}/brief", appId).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(them.post().uri("/api/applications/{id}/brief/sections/PITCH/write", appId).exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(them.put().uri("/api/applications/{id}/brief/sections/COMPANY", appId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\": \"x\"}").exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(writer.inputs).hasSize(1);
    }

    private MvcTestResult get(String appId) {
        return mvc.get().uri("/api/applications/{id}/brief", appId).exchange();
    }

    private MvcTestResult write(String appId, String kind, String interviewId, boolean replaceEdits) {
        var request = mvc.post().uri("/api/applications/{id}/brief/sections/{kind}/write", appId, kind)
                .param("replaceEdits", String.valueOf(replaceEdits));
        if (interviewId != null) request = request.param("interviewId", interviewId);
        return request.exchange();
    }

    private MvcTestResult edit(String appId, String kind, String content, Integer version) {
        return mvc.put().uri("/api/applications/{id}/brief/sections/{kind}", appId, kind)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\": \"%s\", \"version\": %s}".formatted(content, version)).exchange();
    }

    private String createApplication(String body) {
        var result = mvc.post().uri("/api/applications").contentType(MediaType.APPLICATION_JSON).content(body).exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return read(result, "$.id");
    }

    private String createPerson(String name, String role) {
        var result = mvc.post().uri("/api/people").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"%s\", \"role\": \"%s\"}".formatted(name, role)).exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return read(result, "$.id");
    }

    /** A phone screen in UTC; the interviewer, when given, joins the application. */
    private String schedule(String appId, String localStart, String interviewerId, String debriefNotes) {
        var interviewers = interviewerId == null ? List.of() : List.of("\"" + interviewerId + "\"");
        var notes = debriefNotes == null ? "null" : "\"" + debriefNotes + "\"";
        var result = mvc.post().uri("/api/applications/{id}/interviews", appId).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"localStart": "%s", "timeZone": "UTC", "durationMinutes": 30, "type": "PHONE_SCREEN",
                         "interviewerIds": %s, "debriefNotes": %s}
                        """.formatted(localStart, interviewers, notes)).exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return read(result, "$.id");
    }

    private String uploadResume(String filename, String text) {
        var file = new MockMultipartFile("file", filename, "text/plain", text.getBytes());
        var result = mvc.post().uri("/api/documents").multipart().file(file).param("kind", "RESUME").exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return read(result, "$.id");
    }

    private static <T> T read(MvcTestResult result, String path) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), path);
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
