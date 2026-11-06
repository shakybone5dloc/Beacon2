package dev.beacon.api.study;

import com.jayway.jsonpath.JsonPath;
import dev.beacon.DocumentTestDoubles;
import dev.beacon.DocumentTestDoubles.FakeCardWriter;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, DocumentTestDoubles.class})
@Transactional
class StudyApiTests extends SignedInApiTests {

    @Autowired FakeCardWriter writer;
    @Autowired ApiKeyService keys;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        writer.reset();
        keys.save(signedIn.id(), "openai", Map.of("apiKey", "sk-test-users-own"));
    }

    @Test
    void aCardWrittenByHandIsDueStraightAwayAndGradingSchedulesItsNextReview() {
        String deckId = read(post("/api/study/decks", "{\"name\": \"  OAuth basics \"}"), "$.id");
        String cardId = read(post("/api/study/decks/" + deckId + "/cards",
                "{\"question\": \"What does PKCE protect against?\", \"answer\": \"Code interception.\"}"), "$.id");

        assertThat(mvc.get().uri("/api/study/decks/{id}/due", deckId).exchange())
                .bodyJson().extractingPath("$[*].id").asArray().containsExactly(cardId);

        var graded = post("/api/study/cards/" + cardId + "/review", "{\"grade\": \"GOOD\"}");
        assertThat(graded).hasStatus(HttpStatus.OK);
        assertThat(graded).bodyJson().extractingPath("$.intervalDays").isEqualTo(1);
        assertThat(graded).bodyJson().extractingPath("$.repetitions").isEqualTo(1);
        Instant due = Instant.parse(read(graded, "$.dueAt"));
        assertThat(due).isAfter(Instant.now().plus(Duration.ofHours(23)));

        assertThat(mvc.get().uri("/api/study/decks/{id}/due", deckId).exchange())
                .bodyJson().extractingPath("$").asArray().isEmpty();
        var deck = mvc.get().uri("/api/study/decks/{id}", deckId).exchange();
        assertThat(deck).bodyJson().extractingPath("$.name").isEqualTo("OAuth basics");
        assertThat(deck).bodyJson().extractingPath("$.cards").isEqualTo(1);
        assertThat(deck).bodyJson().extractingPath("$.due").isEqualTo(0);
        assertThat(jdbc.queryForObject("select count(*) from study_reviews where card_id = ?::uuid",
                Integer.class, cardId)).isEqualTo(1);
    }

    @Test
    void makesDraftCardsFromTheBriefAndItsStoriesIntoTheApplicationsDeck() {
        var appId = application();
        writeBriefSection(appId, "QUESTIONS", "### Technical\\n\\n- What is OAuth?");
        String storyId = read(post("/api/stories", """
                {"title": "Multi-tenant authorization at PropKnot", "situation": "Landlords shared one app.",
                 "action": "Built row-level policies.", "result": "No leaks.", "tags": ["security"]}
                """), "$.id");
        writeBriefSection(appId, "STORIES", "### Multi-tenant authorization at PropKnot\\n\\nTie to the role.");

        var made = post("/api/applications/" + appId + "/study-cards", "");

        assertThat(made).hasStatus(HttpStatus.OK);
        assertThat(made).bodyJson().extractingPath("$.added").isEqualTo(3);
        assertThat(made).bodyJson().extractingPath("$.deck.name").isEqualTo("GHX – Senior Java Engineer");
        assertThat(made).bodyJson().extractingPath("$.deck.applicationLabel").isEqualTo("GHX – Senior Java Engineer");
        assertThat(made).bodyJson().extractingPath("$.deck.drafts").isEqualTo(3);
        assertThat(made).bodyJson().extractingPath("$.deck.due").isEqualTo(0);
        assertThat(writer.inputs.getFirst().sections()).singleElement()
                .satisfies(section -> assertThat(section.title()).isEqualTo("Likely questions"));

        String deckId = read(made, "$.deck.id");
        var cards = mvc.get().uri("/api/study/decks/{id}/cards", deckId).exchange();
        assertThat(cards).bodyJson().extractingPath("$[*].source").asArray().containsExactly("BRIEF", "BRIEF", "STORY");
        assertThat(cards).bodyJson().extractingPath("$[2].question")
                .isEqualTo("Tell the story: Multi-tenant authorization at PropKnot (security)");
        assertThat(cards).bodyJson().extractingPath("$[2].answer").asString()
                .startsWith("**Situation:** Landlords shared one app.").contains("**Action:** Built row-level policies.");
        assertThat(storyId).isNotBlank();

        // Again: the same questions are skipped, and the same deck is used.
        var again = post("/api/applications/" + appId + "/study-cards", "");
        assertThat(again).bodyJson().extractingPath("$.added").isEqualTo(0);
        assertThat(again).bodyJson().extractingPath("$.skipped").isEqualTo(3);
        assertThat(again).bodyJson().extractingPath("$.deck.id").isEqualTo(deckId);

        var kept = post("/api/study/decks/" + deckId + "/keep-drafts", "");
        assertThat(kept).bodyJson().extractingPath("$.drafts").isEqualTo(0);
        assertThat(kept).bodyJson().extractingPath("$.due").isEqualTo(3);
    }

    @Test
    void aDraftIsKeptBeforeItsStudied() {
        var appId = application();
        writeBriefSection(appId, "REFRESHER", "### OAuth");
        String deckId = read(post("/api/applications/" + appId + "/study-cards", ""), "$.deck.id");
        String cardId = read(mvc.get().uri("/api/study/decks/{id}/cards", deckId).exchange(), "$[0].id");

        assertThat(post("/api/study/cards/" + cardId + "/review", "{\"grade\": \"EASY\"}"))
                .hasStatus(HttpStatus.valueOf(422));
        assertThat(post("/api/study/cards/" + cardId + "/keep", "")).bodyJson()
                .extractingPath("$.status").isEqualTo("ACTIVE");
        assertThat(post("/api/study/cards/" + cardId + "/review", "{\"grade\": \"EASY\"}")).hasStatus(HttpStatus.OK);
    }

    @Test
    void makingCardsNeedsSomethingWrittenInTheBrief() {
        var appId = application();

        assertThat(post("/api/applications/" + appId + "/study-cards", "")).hasStatus(HttpStatus.valueOf(422))
                .bodyJson().extractingPath("$.detail").asString().contains("likely questions or technical refresher");
        assertThat(writer.inputs).isEmpty();
    }

    @Test
    void editingFromAStaleCopyIsAConflict() {
        String deckId = read(post("/api/study/decks", "{\"name\": \"Deck\"}"), "$.id");
        String cardId = read(post("/api/study/decks/" + deckId + "/cards", "{\"question\": \"Q?\"}"), "$.id");

        var edit = """
                {"question": "Q, edited?", "answer": "A.", "version": 0}
                """;
        assertThat(put("/api/study/cards/" + cardId, edit)).hasStatus(HttpStatus.OK);
        assertThat(put("/api/study/cards/" + cardId, edit)).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void anotherUsersDecksAndCardsStayHidden() {
        String deckId = read(post("/api/study/decks", "{\"name\": \"Mine\"}"), "$.id");
        String cardId = read(post("/api/study/decks/" + deckId + "/cards", "{\"question\": \"Q?\"}"), "$.id");
        var them = as(register());

        assertThat(them.get().uri("/api/study/decks").exchange()).bodyJson().extractingPath("$").asArray().isEmpty();
        assertThat(them.get().uri("/api/study/decks/{id}/cards", deckId).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(them.post().uri("/api/study/cards/{id}/review", cardId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"grade\": \"GOOD\"}").exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(them.delete().uri("/api/study/decks/{id}", deckId).exchange()).hasStatus(HttpStatus.NOT_FOUND);
    }

    private String application() {
        return read(post("/api/applications", """
                {"companyName": "GHX", "role": "Senior Java Engineer", "jobDescription": "Java and OAuth."}
                """), "$.id");
    }

    private void writeBriefSection(String appId, String kind, String content) {
        assertThat(put("/api/applications/" + appId + "/brief/sections/" + kind,
                "{\"content\": \"" + content + "\", \"version\": null}")).hasStatus(HttpStatus.OK);
    }

    private MvcTestResult post(String uri, String json) {
        var request = mvc.post().uri(uri);
        return json.isEmpty() ? request.exchange() : request.contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private MvcTestResult put(String uri, String json) {
        return mvc.put().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private static <T> T read(MvcTestResult result, String path) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), path);
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
