package dev.beacon.api.stories;

import com.jayway.jsonpath.JsonPath;
import dev.beacon.DocumentTestDoubles;
import dev.beacon.DocumentTestDoubles.FakeBriefWriter;
import dev.beacon.DocumentTestDoubles.FakeStoryWriter;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, DocumentTestDoubles.class})
@Transactional
class StoryApiTests extends SignedInApiTests {

    private static final String STORY = """
            {"title": "  Multi-tenant authorization at PropKnot ", "situation": "Landlords shared one app.",
             "task": "Keep each landlord's data apart.", "action": "Built row-level policies [which database?].",
             "result": "No cross-tenant leaks in [how many months?].", "metrics": "[how many tenants?]",
             "tags": [" Auth You've Built", "security", "SECURITY", " "]}
            """;

    @Autowired FakeStoryWriter writer;
    @Autowired FakeBriefWriter briefWriter;
    @Autowired ApiKeyService keys;

    @BeforeEach
    void setUp() {
        writer.reset();
        briefWriter.reset();
        keys.save(signedIn.id(), "openai", Map.of("apiKey", "sk-test-users-own"));
    }

    @Test
    void savesAStoryWithItsTagsCleanedAndItsGapsFound() {
        var created = create(STORY);

        assertThat(created).hasStatus(HttpStatus.CREATED);
        assertThat(created).bodyJson().extractingPath("$.title").isEqualTo("Multi-tenant authorization at PropKnot");
        assertThat(created).bodyJson().extractingPath("$.tags").asArray()
                .containsExactly("auth you've built", "security");
        assertThat(created).bodyJson().extractingPath("$.gaps").asArray()
                .containsExactly("which database?", "how many months?", "how many tenants?");

        var list = mvc.get().uri("/api/stories").exchange();
        assertThat(list).bodyJson().extractingPath("$[*].title").asArray()
                .containsExactly("Multi-tenant authorization at PropKnot");
    }

    @Test
    void filledInGapsGoAwayAndAStaleEditIsAConflict() {
        String id = read(create(STORY), "$.id");

        var updated = update(id, """
                {"title": "Multi-tenant authorization", "action": "Built row-level policies in Postgres.",
                 "result": "No leaks in 18 months.", "metrics": "40 tenants. See [the design](https://x.example).",
                 "tags": ["security"], "version": 0}
                """);
        assertThat(updated).hasStatus(HttpStatus.OK);
        assertThat(updated).bodyJson().extractingPath("$.gaps").asArray().isEmpty();
        assertThat(updated).bodyJson().extractingPath("$.situation").isEqualTo("");

        assertThat(update(id, """
                {"title": "Old copy", "version": 0}
                """)).hasStatus(HttpStatus.CONFLICT);
        assertThat(mvc.delete().uri("/api/stories/{id}", id).exchange()).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/stories/{id}", id).exchange()).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void aStoryNeedsATitle() {
        var result = create("""
                {"title": "  ", "action": "Did things."}
                """);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errors.title").isNotNull();
    }

    @Test
    void draftsOneStoryFromNotesWithoutSavingIt() {
        var drafts = draft("""
                {"notes": "  Made checkout fast by caching prices.  "}
                """);

        assertThat(drafts).hasStatus(HttpStatus.OK);
        assertThat(drafts).bodyJson().extractingPath("$[*].title").asArray().containsExactly("Cut checkout latency");
        assertThat(drafts).bodyJson().extractingPath("$[0].tags").asArray()
                .containsExactly("performance", "hardest problem");
        assertThat(drafts).bodyJson().extractingPath("$[0].gaps").asArray().containsExactly("how much faster?");
        assertThat(writer.inputs.getFirst().notes()).isEqualTo("Made checkout fast by caching prices.");
        assertThat(writer.inputs.getFirst().resume()).isNull();
        assertThat(mvc.get().uri("/api/stories").exchange()).bodyJson().extractingPath("$").asArray().isEmpty();
    }

    @Test
    void suggestsStoriesFromTheDefaultResumeWhenThereAreNoNotes() {
        assertThat(draft("{}")).hasStatus(HttpStatus.valueOf(422))
                .bodyJson().extractingPath("$.detail").asString().contains("notes on the story, or a resume");

        uploadResume("Jordan Smith\nCut checkout latency at Acme.");
        var drafts = draft("{}");

        assertThat(drafts).bodyJson().extractingPath("$[*].title").asArray()
                .containsExactly("Cut checkout latency", "Mentored two juniors");
        assertThat(writer.inputs.getLast().notes()).isNull();
        assertThat(writer.inputs.getLast().resume()).contains("checkout latency");
        assertThat(mvc.get().uri("/api/me/ai-usage?zone=UTC").exchange())
                .bodyJson().extractingPath("$.byFeature[*].name").asArray().contains("STORY_BANK");
    }

    @Test
    void draftingNeedsAnAiKey() {
        keys.delete(signedIn.id(), "openai");

        assertThat(draft("""
                {"notes": "Made checkout fast."}
                """)).hasStatus(HttpStatus.valueOf(422))
                .bodyJson().extractingPath("$.detail").asString().contains("API key");
        assertThat(writer.inputs).isEmpty();
    }

    @Test
    void theBriefPicksStoriesFromTheBank() {
        create(STORY);
        uploadResume("Jordan Smith\nJava");
        var appId = read(mvc.post().uri("/api/applications").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"companyName": "GHX", "role": "Engineer", "jobDescription": "Secure multi-tenant SaaS."}
                        """).exchange(), "$.id");

        assertThat(mvc.post().uri("/api/applications/{id}/brief/sections/STORIES/write", appId).exchange())
                .hasStatus(HttpStatus.OK);

        assertThat(briefWriter.inputs.getFirst().stories()).singleElement().satisfies(story -> {
            assertThat(story.title()).isEqualTo("Multi-tenant authorization at PropKnot");
            assertThat(story.tags()).containsExactly("auth you've built", "security");
            assertThat(story.metrics()).isEqualTo("[how many tenants?]");
        });
    }

    @Test
    void anotherUsersStoriesStayHidden() {
        String id = read(create(STORY), "$.id");
        var them = as(register());

        assertThat(them.get().uri("/api/stories").exchange()).bodyJson().extractingPath("$").asArray().isEmpty();
        assertThat(them.get().uri("/api/stories/{id}", id).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(them.put().uri("/api/stories/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"Mine now\", \"version\": 0}").exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(them.delete().uri("/api/stories/{id}", id).exchange()).hasStatus(HttpStatus.NOT_FOUND);
    }

    private MvcTestResult create(String json) {
        return mvc.post().uri("/api/stories").contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private MvcTestResult update(String id, String json) {
        return mvc.put().uri("/api/stories/{id}", id).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private MvcTestResult draft(String json) {
        return mvc.post().uri("/api/stories/drafts").contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private void uploadResume(String text) {
        var file = new MockMultipartFile("file", "backend.txt", "text/plain", text.getBytes());
        assertThat(mvc.post().uri("/api/documents").multipart().file(file).param("kind", "RESUME").exchange())
                .hasStatus(HttpStatus.CREATED);
    }

    private static <T> T read(MvcTestResult result, String path) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), path);
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
