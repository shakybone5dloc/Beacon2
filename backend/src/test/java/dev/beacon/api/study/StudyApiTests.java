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

    @Test
    void gradingAimsTheNextReviewAtTheApplicationsInterview() {
        var appId = application();
        var cardId = keptCard(appId);
        var interviewAt = scheduleInterview(appId, Duration.ofHours(84)); // 3.5 days

        // Easy on a new card means 3 days, which would skip the interview: it comes back the day before.
        var graded = post("/api/study/cards/" + cardId + "/review", "{\"grade\": \"EASY\"}");

        assertThat(Instant.parse(read(graded, "$.dueAt"))).isEqualTo(interviewAt.minus(Duration.ofDays(1)));
        assertThat(graded).bodyJson().extractingPath("$.intervalDays").isEqualTo(3);
    }

    @Test
    void inTheLastDayBeforeAnInterviewEveryCardNotSeenSinceIsDueAndCountedOnTheApplication() {
        var appId = application();
        var cardId = keptCard(appId);
        // Learned well a few days ago and not due for another ten.
        jdbc.update("update study_cards set due_at = now() + interval '10 days', last_reviewed_at = now() - interval '3 days' "
                + "where id = ?::uuid", cardId);
        assertThat(mvc.get().uri("/api/applications/{id}", appId).exchange())
                .bodyJson().extractingPath("$.studyDue").isEqualTo(0);

        scheduleInterview(appId, Duration.ofHours(12));

        assertThat(mvc.get().uri("/api/applications/{id}", appId).exchange())
                .bodyJson().extractingPath("$.studyDue").isEqualTo(1);
        assertThat(mvc.get().uri("/api/applications").exchange())
                .bodyJson().extractingPath("$[0].studyDue").isEqualTo(1);
        String deckId = jdbc.queryForObject("select deck_id::text from study_cards where id = ?::uuid", String.class, cardId);
        assertThat(mvc.get().uri("/api/study/decks/{id}/due", deckId).exchange())
                .bodyJson().extractingPath("$[*].id").asArray().containsExactly(cardId);
        assertThat(mvc.get().uri("/api/study/decks/{id}", deckId).exchange())
                .bodyJson().extractingPath("$.due").isEqualTo(1);

        // Seen once in that last day, it waits: about two hours before the interview at the latest.
        post("/api/study/cards/" + cardId + "/review", "{\"grade\": \"GOOD\"}");
        assertThat(mvc.get().uri("/api/applications/{id}", appId).exchange())
                .bodyJson().extractingPath("$.studyDue").isEqualTo(0);
    }

    @Test
    void studyingForAnInterviewAddsItsPrepNotesToTheBrief() {
        var appId = application();
        writeBriefSection(appId, "QUESTIONS", "- What is OAuth?");
        String interviewId = read(post("/api/applications/" + appId + "/interviews", """
                {"localStart": "2099-03-10T14:00:00", "timeZone": "UTC", "durationMinutes": 45, "type": "TECHNICAL",
                 "prepNotes": "Review Kafka consumer groups."}
                """), "$.id");

        var made = post("/api/interviews/" + interviewId + "/study-cards", "");

        assertThat(made).hasStatus(HttpStatus.OK);
        assertThat(made).bodyJson().extractingPath("$.deck.applicationId").isEqualTo(appId);
        assertThat(made).bodyJson().extractingPath("$.added").isEqualTo(2);
        var sections = writer.inputs.getFirst().sections();
        assertThat(sections).extracting(s -> s.title())
                .containsExactly("Likely questions", "Prep notes for this interview");
        assertThat(sections.get(1).markdown()).isEqualTo("Review Kafka consumer groups.");

        // Again: the same questions are skipped, and it's still the one deck.
        var again = post("/api/interviews/" + interviewId + "/study-cards", "");
        assertThat(again).bodyJson().extractingPath("$.added").isEqualTo(0);
        assertThat(again).bodyJson().extractingPath("$.deck.id").isEqualTo(read(made, "$.deck.id"));
    }

    @Test
    void withoutABriefTheCardsComeFromThePostingAndTheFitGaps() {
        var appId = application();
        String interviewId = read(post("/api/applications/" + appId + "/interviews", """
                {"localStart": "2099-03-10T14:00:00", "timeZone": "UTC", "durationMinutes": 45, "type": "TECHNICAL"}
                """), "$.id");
        var resumeId = java.util.UUID.randomUUID();
        jdbc.update("""
                insert into documents (id, owner_id, kind, filename, content_type, size_bytes, content, created_at)
                values (?, ?, 'RESUME', 'r.txt', 'text/plain', 1, 'x'::bytea, now())
                """, resumeId, signedIn.id());
        String fitId = java.util.UUID.randomUUID().toString();
        jdbc.update("""
                insert into fit_scores (id, owner_id, resume_document_id, input_hash, score, gaps, rationale,
                                        provider, model, created_at)
                values (?::uuid, ?, ?, 'h', 70, '{Kafka,Kubernetes}', 'ok', 'openai', 'm', now())
                """, fitId, signedIn.id(), resumeId);
        jdbc.update("insert into application_fits (application_id, fit_score_id, linked_at) values (?::uuid, ?::uuid, now())",
                appId, fitId);

        assertThat(post("/api/interviews/" + interviewId + "/study-cards", "")).hasStatus(HttpStatus.OK);

        var sections = writer.inputs.getFirst().sections();
        assertThat(sections).extracting(s -> s.title()).containsExactly("Gaps from the fit check", "Job posting");
        assertThat(sections.get(0).markdown()).isEqualTo("- Kafka\n- Kubernetes");
        assertThat(sections.get(1).markdown()).isEqualTo("Java and OAuth.");
    }

    @Test
    void studyingForAnInterviewNeedsSomethingToMakeCardsFrom() {
        String appId = read(post("/api/applications", "{\"companyName\": \"GHX\", \"role\": \"Engineer\"}"), "$.id");
        String interviewId = read(post("/api/applications/" + appId + "/interviews", """
                {"localStart": "2099-03-10T14:00:00", "timeZone": "UTC", "durationMinutes": 45, "type": "TECHNICAL"}
                """), "$.id");

        var made = post("/api/interviews/" + interviewId + "/study-cards", "");

        assertThat(made).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(made).bodyText().contains("Add prep notes to this interview");
        assertThat(writer.inputs).isEmpty();
        assertThat(post("/api/interviews/" + java.util.UUID.randomUUID() + "/study-cards", ""))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void drillsStoriesIntoOneStoriesDeckAndTheCardsFollowEdits() {
        String first = read(post("/api/stories", """
                {"title": "Multi-tenant authorization", "situation": "Landlords shared one app.",
                 "action": "Built row-level policies.", "result": "No leaks.", "tags": ["security"]}
                """), "$.id");
        String second = read(post("/api/stories", "{\"title\": \"Legacy to cloud\", \"tags\": []}"), "$.id");

        var drilled = post("/api/study/stories", "{\"storyIds\": [\"" + first + "\"]}");

        assertThat(drilled).hasStatus(HttpStatus.OK);
        assertThat(drilled).bodyJson().extractingPath("$.deck.kind").isEqualTo("STORIES");
        assertThat(drilled).bodyJson().extractingPath("$.deck.name").isEqualTo("Stories");
        assertThat(drilled).bodyJson().extractingPath("$.added").isEqualTo(1);
        // Kept straight away: they're the user's own words.
        assertThat(drilled).bodyJson().extractingPath("$.deck.due").isEqualTo(1);
        String deckId = read(drilled, "$.deck.id");
        String cardId = read(mvc.get().uri("/api/study/decks/{id}/cards", deckId).exchange(), "$[0].id");
        assertThat(mvc.get().uri("/api/study/decks/{id}/cards", deckId).exchange()).bodyJson()
                .extractingPath("$[0].question").isEqualTo("Tell the story: Multi-tenant authorization (security)");

        // Every story: the one already drilled is skipped, and it's still the one deck.
        var all = post("/api/study/stories", "{}");
        assertThat(all).bodyJson().extractingPath("$.deck.id").isEqualTo(deckId);
        assertThat(all).bodyJson().extractingPath("$.added").isEqualTo(1);
        assertThat(all).bodyJson().extractingPath("$.skipped").isEqualTo(1);

        // Studied, then the story is improved: the card's answer follows and its schedule stays.
        post("/api/study/cards/" + cardId + "/review", "{\"grade\": \"GOOD\"}");
        String dueAt = jdbc.queryForObject("select due_at::text from study_cards where id = ?::uuid", String.class, cardId);
        Integer version = JsonPath.read(content(mvc.get().uri("/api/stories/{id}", first).exchange()), "$.version");
        assertThat(put("/api/stories/" + first, """
                {"title": "Multi-tenant authorization", "situation": "Landlords shared one app.",
                 "action": "Built row-level policies.", "result": "No leaks across 40 tenants.", "tags": ["security"],
                 "version": %d}
                """.formatted(version))).hasStatus(HttpStatus.OK);

        var card = jdbc.queryForMap("select answer, due_at::text as due_at from study_cards where id = ?::uuid", cardId);
        assertThat((String) card.get("answer")).contains("No leaks across 40 tenants.");
        assertThat(card.get("due_at")).isEqualTo(dueAt);
        assertThat(second).isNotNull();
    }

    @Test
    void drillingNeedsAStory() {
        var drilled = post("/api/study/stories", "{}");

        assertThat(drilled).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(drilled).bodyText().contains("Write a story first");
    }

    /** One kept card in the application's own deck, made from its brief. */
    private String keptCard(String appId) {
        writeBriefSection(appId, "QUESTIONS", "- What is OAuth?");
        String deckId = read(post("/api/applications/" + appId + "/study-cards", ""), "$.deck.id");
        post("/api/study/decks/" + deckId + "/keep-drafts", "");
        jdbc.update("delete from study_cards where deck_id = ?::uuid and id <> "
                + "(select id from study_cards where deck_id = ?::uuid order by created_at limit 1)", deckId, deckId);
        return jdbc.queryForObject("select id::text from study_cards where deck_id = ?::uuid", String.class, deckId);
    }

    /** An interview this far from now, in UTC; returns when it starts. */
    private Instant scheduleInterview(String appId, Duration fromNow) {
        var start = Instant.now().plus(fromNow).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        var local = java.time.LocalDateTime.ofInstant(start, java.time.ZoneOffset.UTC);
        assertThat(post("/api/applications/" + appId + "/interviews", """
                {"localStart": "%s", "timeZone": "UTC", "durationMinutes": 45, "type": "TECHNICAL"}
                """.formatted(local))).hasStatus(HttpStatus.CREATED);
        return start;
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

    private static String content(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static <T> T read(MvcTestResult result, String path) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), path);
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
