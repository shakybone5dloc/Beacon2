package dev.beacon.api.searches;

import com.jayway.jsonpath.JsonPath;
import dev.beacon.DocumentTestDoubles;
import dev.beacon.DocumentTestDoubles.FakeFitScorer;
import dev.beacon.SignedInApiTests;
import dev.beacon.TestcontainersConfiguration;
import dev.beacon.application.apikeys.ApiKeyService;
import dev.beacon.application.postings.JobPosting;
import dev.beacon.application.postings.JobSource;
import dev.beacon.application.postings.PostingQuery;
import dev.beacon.application.usage.AiFeature;
import dev.beacon.application.usage.AiUsage;
import dev.beacon.application.usage.AiUsageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({TestcontainersConfiguration.class, DocumentTestDoubles.class, SavedSearchApiTests.FakeBoardConfig.class})
@Transactional
class SavedSearchApiTests extends SignedInApiTests {

    /** A job board whose postings each test sets; searches name it alone, so no real site is called. */
    @TestConfiguration
    static class FakeBoardConfig {
        static final List<JobPosting> POSTINGS = new ArrayList<>();

        @Bean
        JobSource fakeBoard() {
            return new JobSource() {
                @Override public String id() { return "fakeboard"; }
                @Override public String name() { return "Fake Board"; }
                @Override public String coverage() { return "Test postings"; }
                @Override public String attributionUrl() { return "https://fakeboard.example"; }

                @Override
                public List<JobPosting> search(PostingQuery query, Map<String, String> credentials) {
                    return List.copyOf(POSTINGS);
                }
            };
        }
    }

    private static final String SEARCH = """
            {"name": "Java remote", "keywords": "java", "sources": ["fakeboard"], "excludeWords": ["Senior"],
             "runDays": ["FRIDAY", "MONDAY"], "runHour": 7, "timeZone": "America/New_York"%s}
            """;

    @Autowired FakeFitScorer scorer;
    @Autowired ApiKeyService keys;
    @Autowired AiUsageService usage;

    @BeforeEach
    void setUp() {
        scorer.reset();
        FakeBoardConfig.POSTINGS.clear();
    }

    @Test
    void aSearchIsSavedWithItsNextRunAndListed() {
        var created = create(SEARCH.formatted(""));

        assertThat(created).hasStatus(HttpStatus.CREATED);
        assertThat(created).bodyJson().extractingPath("$.runDays").asArray().containsExactly("MONDAY", "FRIDAY");
        assertThat(created).bodyJson().extractingPath("$.postedWithinDays").isEqualTo(7);
        assertThat(created).bodyJson().extractingPath("$.active").isEqualTo(true);
        assertThat(created).bodyJson().extractingPath("$.nextRunAt").isNotNull();
        assertThat(created).bodyJson().extractingPath("$.lastRun").isNull();
        assertThat(mvc.get().uri("/api/saved-searches").exchange())
                .bodyJson().extractingPath("$[*].name").asArray().containsExactly("Java remote");
    }

    @Test
    void settingsThatCantBeUsedAreRejectedByField() {
        assertThat(create(SEARCH.formatted("").replace("fakeboard", "nowhere")))
                .bodyJson().extractingPath("$.errors.sources").isNotNull();
        assertThat(create(SEARCH.formatted("").replace("America/New_York", "Mars/Olympus")))
                .bodyJson().extractingPath("$.errors.timeZone").isNotNull();
        assertThat(create(SEARCH.formatted("").replace("[\"FRIDAY\", \"MONDAY\"]", "[]")))
                .bodyJson().extractingPath("$.errors.runDays").isNotNull();

        var someoneElsesResume = uploadResume(as(register()));
        assertThat(create(SEARCH.formatted(", \"resumeDocumentId\": \"" + someoneElsesResume + "\"")))
                .bodyJson().extractingPath("$.errors.resumeDocumentId").isNotNull();
    }

    @Test
    void aRunKeepsNewPostingsAsMatchesAndLeavesOutWhatsKnownOrFiltered() {
        createApplication("Acme, Inc.", "Java Engineer");
        FakeBoardConfig.POSTINGS.addAll(List.of(
                posting("1", "Java Engineer", "ACME"),           // already on the board
                posting("2", "Senior Java Developer", "Globex"), // excluded word
                posting("3", "Java Developer", "Initech")));
        var id = id(create(SEARCH.formatted("")));

        var run = run(id);

        assertThat(run).hasStatus(HttpStatus.ACCEPTED);
        var search = mvc.get().uri("/api/saved-searches/{id}", id).exchange();
        assertThat(search).bodyJson().extractingPath("$.running").isEqualTo(false);
        assertThat(search).bodyJson().extractingPath("$.newMatches").isEqualTo(1);
        assertThat(search).bodyJson().extractingPath("$.lastRun.gathered").isEqualTo(3);
        assertThat(search).bodyJson().extractingPath("$.lastRun.newMatches").isEqualTo(1);
        assertThat(search).bodyJson().extractingPath("$.lastRun.notes").asArray().contains("Fake Board: 3");
        var matches = mvc.get().uri("/api/matches").exchange();
        assertThat(matches).bodyJson().extractingPath("$[*].title").asArray().containsExactly("Java Developer");
        assertThat(matches).bodyJson().extractingPath("$[0].searchName").isEqualTo("Java remote");
        assertThat(matches).bodyJson().extractingPath("$[0].score").isNull();
        assertThat(mvc.get().uri("/api/matches/summary").exchange()).bodyJson().extractingPath("$.newCount").isEqualTo(1);

        // Tomorrow the same job comes back, written a little differently: still one match.
        FakeBoardConfig.POSTINGS.set(2, posting("3b", "Java developer", "Initech, Inc."));
        run(id);
        assertThat(mvc.get().uri("/api/saved-searches/{id}", id).exchange())
                .bodyJson().extractingPath("$.lastRun.newMatches").isEqualTo(0);
        assertThat(mvc.get().uri("/api/matches").exchange()).bodyJson().extractingPath("$").asArray().hasSize(1);
    }

    @Test
    void oldPostingsAndOnesPayingUnderTheFloorAreLeftOut() {
        FakeBoardConfig.POSTINGS.addAll(List.of(
                withPostedAt(posting("1", "Java Developer", "Old Co"), Instant.now().minus(30, ChronoUnit.DAYS)),
                withPay(posting("2", "Java Developer", "Cheap Co"), 60_000),
                withPay(posting("3", "Java Developer", "Fair Co"), 150_000),
                posting("4", "Java Developer", "Quiet Co")));
        var id = id(create(SEARCH.formatted(", \"salaryMin\": 100000")));

        run(id);

        assertThat(mvc.get().uri("/api/matches").exchange())
                .bodyJson().extractingPath("$[*].company").asArray().containsExactlyInAnyOrder("Fair Co", "Quiet Co");
    }

    @Test
    void withAResumeNewMatchesAreScoredAndLowScoresKeptOutOfSight() {
        keys.save(signedIn.id(), "openai", Map.of("apiKey", "sk-test"));
        var resumeId = uploadResume(mvc);
        FakeBoardConfig.POSTINGS.add(posting("1", "Java Developer", "Initech"));
        var shown = id(create(SEARCH.formatted(", \"resumeDocumentId\": \"" + resumeId + "\", \"minScore\": 80")));

        run(shown);

        assertThat(mvc.get().uri("/api/matches").exchange()).bodyJson().extractingPath("$[0].score").isEqualTo(82);
        assertThat(mvc.get().uri("/api/saved-searches/{id}", shown).exchange())
                .bodyJson().extractingPath("$.lastRun.scored").isEqualTo(1);
        assertThat(scorer.inputs.getFirst().jobDescription()).isEqualTo("Build services with Java.");
        assertThat(mvc.get().uri("/api/me/ai-usage").exchange()).bodyJson().extractingPath("$.calls").isEqualTo(1);

        FakeBoardConfig.POSTINGS.set(0, posting("2", "Java Developer", "Hooli"));
        var picky = id(create(SEARCH.formatted(", \"resumeDocumentId\": \"" + resumeId + "\", \"minScore\": 90")));
        run(picky);

        assertThat(mvc.get().uri("/api/matches").exchange())
                .bodyJson().extractingPath("$[*].company").asArray().containsExactly("Initech");
        assertThat(mvc.get().uri("/api/matches?status=BELOW_MIN").exchange())
                .bodyJson().extractingPath("$[*].company").asArray().containsExactly("Hooli");
    }

    @Test
    void scoringPausesAtTheMonthlyBudget() {
        keys.save(signedIn.id(), "openai", Map.of("apiKey", "sk-test"));
        usage.setBudget(signedIn.id(), new BigDecimal("1.00"));
        usage.record(signedIn.id(), AiFeature.COVER_LETTER, new AiUsage("openai", "gpt-6-sol", 500_000, 0, 0));
        var resumeId = uploadResume(mvc);
        FakeBoardConfig.POSTINGS.add(posting("1", "Java Developer", "Initech"));
        var id = id(create(SEARCH.formatted(", \"resumeDocumentId\": \"" + resumeId + "\"")));

        run(id);

        assertThat(scorer.inputs).isEmpty();
        assertThat(mvc.get().uri("/api/matches").exchange()).bodyJson().extractingPath("$[0].score").isNull();
        assertThat(mvc.get().uri("/api/saved-searches/{id}", id).exchange())
                .bodyJson().extractingPath("$.lastRun.notes").asArray()
                .contains("Fit checks paused: you've reached your monthly AI budget.");
    }

    @Test
    void savingAMatchPutsItOnTheBoardWithItsDeadlineAndFit() {
        keys.save(signedIn.id(), "openai", Map.of("apiKey", "sk-test"));
        var resumeId = uploadResume(mvc);
        FakeBoardConfig.POSTINGS.add(posting("1", "Java Developer", "Initech"));
        run(id(create(SEARCH.formatted(", \"resumeDocumentId\": \"" + resumeId + "\""))));
        var matchId = JsonPath.<String>read(body(mvc.get().uri("/api/matches").exchange()), "$[0].id");

        var saved = mvc.post().uri("/api/matches/{id}/save", matchId).exchange();

        assertThat(saved).hasStatus(HttpStatus.OK);
        assertThat(saved).bodyJson().extractingPath("$.status").isEqualTo("SAVED");
        assertThat(saved).bodyJson().extractingPath("$.company").isEqualTo("Initech");
        assertThat(saved).bodyJson().extractingPath("$.role").isEqualTo("Java Developer");
        assertThat(saved).bodyJson().extractingPath("$.applyBy").isEqualTo(LocalDate.now().plusDays(20).toString());
        assertThat(saved).bodyJson().extractingPath("$.source").isEqualTo("Fake Board");
        assertThat(saved).bodyJson().extractingPath("$.fitScore").isEqualTo(82);
        assertThat(mvc.get().uri("/api/matches").exchange()).bodyJson().extractingPath("$").asArray().isEmpty();
        var appId = JsonPath.<String>read(body(saved), "$.id");
        assertThat(mvc.post().uri("/api/matches/{id}/save", matchId).exchange())
                .bodyJson().extractingPath("$.id").isEqualTo(appId);
        // Only the one scoring call: linking the fit to the application cost nothing.
        assertThat(scorer.inputs).hasSize(1);
    }

    @Test
    void aDismissedMatchStaysGoneUntilRestored() {
        FakeBoardConfig.POSTINGS.add(posting("1", "Java Developer", "Initech"));
        var id = id(create(SEARCH.formatted("")));
        run(id);
        var matchId = JsonPath.<String>read(body(mvc.get().uri("/api/matches").exchange()), "$[0].id");

        assertThat(mvc.post().uri("/api/matches/{id}/dismiss", matchId).exchange())
                .bodyJson().extractingPath("$.status").isEqualTo("DISMISSED");
        run(id);
        assertThat(mvc.get().uri("/api/matches").exchange()).bodyJson().extractingPath("$").asArray().isEmpty();

        assertThat(mvc.post().uri("/api/matches/{id}/restore", matchId).exchange())
                .bodyJson().extractingPath("$.status").isEqualTo("NEW");
        assertThat(mvc.get().uri("/api/matches").exchange()).bodyJson().extractingPath("$").asArray().hasSize(1);
    }

    @Test
    void anotherUsersSearchesAndMatchesAreNotFound() {
        FakeBoardConfig.POSTINGS.add(posting("1", "Java Developer", "Initech"));
        var id = id(create(SEARCH.formatted("")));
        run(id);
        var matchId = JsonPath.<String>read(body(mvc.get().uri("/api/matches").exchange()), "$[0].id");
        var other = as(register());

        assertThat(other.get().uri("/api/saved-searches/{id}", id).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(other.post().uri("/api/saved-searches/{id}/run", id).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(other.post().uri("/api/matches/{id}/save", matchId).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(other.get().uri("/api/matches").exchange()).bodyJson().extractingPath("$").asArray().isEmpty();
    }

    @Test
    void editingNeedsTheLoadedVersionAndDeletingKeepsTheMatches() {
        FakeBoardConfig.POSTINGS.add(posting("1", "Java Developer", "Initech"));
        var id = id(create(SEARCH.formatted("")));
        run(id);
        // A run bumps the version too, so edit with whatever was loaded last.
        int version = JsonPath.read(body(mvc.get().uri("/api/saved-searches/{id}", id).exchange()), "$.version");

        var paused = mvc.put().uri("/api/saved-searches/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content(SEARCH.formatted(", \"active\": false, \"version\": " + version)).exchange();
        assertThat(paused).hasStatus(HttpStatus.OK);
        assertThat(paused).bodyJson().extractingPath("$.active").isEqualTo(false);
        assertThat(paused).bodyJson().extractingPath("$.nextRunAt").isNull();
        assertThat(mvc.put().uri("/api/saved-searches/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content(SEARCH.formatted(", \"version\": " + version)).exchange()).hasStatus(HttpStatus.CONFLICT);

        assertThat(mvc.delete().uri("/api/saved-searches/{id}", id).exchange()).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/matches").exchange())
                .bodyJson().extractingPath("$[0].searchName").isNull();
    }

    private static JobPosting posting(String id, String title, String company) {
        return new JobPosting("fakeboard", id, title, company, "Remote", true, "https://fakeboard.example/" + id,
                Instant.now().minus(1, ChronoUnit.DAYS), LocalDate.now().plusDays(20), null, null, null, null, null,
                List.of(), "Java role", "Build services with Java.");
    }

    private static JobPosting withPostedAt(JobPosting p, Instant postedAt) {
        return new JobPosting(p.source(), p.id(), p.title(), p.company(), p.location(), p.remote(), p.url(), postedAt,
                p.closesOn(), p.salaryMin(), p.salaryMax(), p.salaryCurrency(), p.salaryPeriod(), p.salaryText(),
                p.tags(), p.snippet(), p.description());
    }

    private static JobPosting withPay(JobPosting p, int max) {
        return new JobPosting(p.source(), p.id(), p.title(), p.company(), p.location(), p.remote(), p.url(), p.postedAt(),
                p.closesOn(), max - 20_000, max, "USD", "year", null, p.tags(), p.snippet(), p.description());
    }

    private MvcTestResult create(String json) {
        return mvc.post().uri("/api/saved-searches").contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private MvcTestResult run(String id) {
        return mvc.post().uri("/api/saved-searches/{id}/run", id).exchange();
    }

    private void createApplication(String company, String role) {
        var result = mvc.post().uri("/api/applications").contentType(MediaType.APPLICATION_JSON)
                .content("{\"companyName\": \"%s\", \"role\": \"%s\"}".formatted(company, role)).exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
    }

    private static String uploadResume(MockMvcTester as) {
        var file = new MockMultipartFile("file", "cv.txt", "text/plain", "Jordan Smith\nJava, Spring Boot".getBytes());
        var result = as.post().uri("/api/documents").multipart().file(file).param("kind", "RESUME").exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return JsonPath.read(body(result), "$.id");
    }

    private static String id(MvcTestResult result) {
        return JsonPath.read(body(result), "$.id");
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
