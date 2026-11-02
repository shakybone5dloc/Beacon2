package dev.beacon.api.usage;

import com.jayway.jsonpath.JsonPath;
import dev.beacon.DocumentTestDoubles;
import dev.beacon.SignedInApiTests;
import dev.beacon.TestcontainersConfiguration;
import dev.beacon.application.apikeys.ApiKeyService;
import dev.beacon.application.usage.AiFeature;
import dev.beacon.application.usage.AiUsage;
import dev.beacon.application.usage.AiUsageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.UnsupportedEncodingException;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({TestcontainersConfiguration.class, DocumentTestDoubles.class})
@Transactional
class AiUsageApiTests extends SignedInApiTests {

    @Autowired AiUsageService usage;
    @Autowired ApiKeyService keys;

    @BeforeEach
    void saveKey() {
        keys.save(signedIn.id(), "openai", Map.of("apiKey", "sk-test"));
    }

    @Test
    void anAiCallIsCountedAndPricedByFeatureAndModel() {
        assertThat(scorePosting(uploadResume())).hasStatus(HttpStatus.OK);
        // A model with no price is still counted, apart from the totals.
        usage.record(signedIn.id(), AiFeature.COVER_LETTER, new AiUsage("anthropic", "claude-future-9", 5000, 0, 900));

        var summary = mvc.get().uri("/api/me/ai-usage?zone=America/New_York").exchange();

        assertThat(summary).hasStatus(HttpStatus.OK);
        assertThat(summary).bodyJson().extractingPath("$.month")
                .isEqualTo(YearMonth.now(ZoneId.of("America/New_York")).toString());
        // 800 uncached input at $2, 200 cached at $0.20 and 500 output at $10 per million.
        assertThat(summary).bodyJson().extractingPath("$.totalUsd").isEqualTo(0.00664);
        assertThat(summary).bodyJson().extractingPath("$.calls").isEqualTo(2);
        assertThat(summary).bodyJson().extractingPath("$.unpricedCalls").isEqualTo(1);
        assertThat(summary).bodyJson().extractingPath("$.byFeature[*].name").asArray()
                .containsExactly("FIT_SCORE", "COVER_LETTER");
        assertThat(summary).bodyJson().extractingPath("$.byFeature[0].inputTokens").isEqualTo(1000);
        assertThat(summary).bodyJson().extractingPath("$.byModel[*].name").asArray()
                .containsExactly("gpt-6-sol", "claude-future-9");
        assertThat(summary).bodyJson().extractingPath("$.recentMonths").asArray().hasSize(6);
        assertThat(summary).bodyJson().extractingPath("$.recentMonths[5].calls").isEqualTo(2);
        assertThat(summary).bodyJson().extractingPath("$.recentMonths[0].calls").isEqualTo(0);
    }

    @Test
    void aKeptFitScoreCostsNothing() {
        var resumeId = uploadResume();
        assertThat(scorePosting(resumeId)).hasStatus(HttpStatus.OK);
        assertThat(scorePosting(resumeId)).hasStatus(HttpStatus.OK);

        assertThat(mvc.get().uri("/api/me/ai-usage").exchange()).bodyJson().extractingPath("$.calls").isEqualTo(1);
    }

    @Test
    void eachUserSeesOnlyTheirOwnSpending() {
        usage.record(signedIn.id(), AiFeature.FIT_SCORE, new AiUsage("openai", "gpt-6-sol", 1_000_000, 0, 0));

        var other = as(register()).get().uri("/api/me/ai-usage").exchange();

        assertThat(other).bodyJson().extractingPath("$.calls").isEqualTo(0);
        assertThat(other).bodyJson().extractingPath("$.totalUsd").isEqualTo(0);
        assertThat(mvc.get().uri("/api/me/ai-usage").exchange()).bodyJson().extractingPath("$.totalUsd").isEqualTo(2.0);
    }

    @Test
    void aBudgetCanBeSetAndRemoved() {
        var saved = saveBudget("{\"monthlyUsd\": 12.50}");
        assertThat(saved).hasStatus(HttpStatus.OK);
        assertThat(saved).bodyJson().extractingPath("$.budgetUsd").isEqualTo(12.5);

        var tooSmall = saveBudget("{\"monthlyUsd\": 0.5}");
        assertThat(tooSmall).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(tooSmall).bodyJson().extractingPath("$.errors.monthlyUsd").isNotNull();

        assertThat(saveBudget("{\"monthlyUsd\": null}")).bodyJson().extractingPath("$.budgetUsd").isNull();
    }

    @Test
    void anUnknownTimeZoneIsRejected() {
        var result = mvc.get().uri("/api/me/ai-usage?zone=Mars/Olympus").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errors.zone").isNotNull();
    }

    private String uploadResume() {
        var file = new MockMultipartFile("file", "cv.txt", "text/plain", "Jordan Smith\nJava, Spring Boot".getBytes());
        var result = mvc.post().uri("/api/documents").multipart().file(file).param("kind", "RESUME").exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private MvcTestResult scorePosting(String resumeId) {
        return mvc.post().uri("/api/fit").contentType(MediaType.APPLICATION_JSON).content("""
                {"resumeDocumentId": "%s", "title": "Java Engineer", "description": "Build services with Java."}
                """.formatted(resumeId)).exchange();
    }

    private MvcTestResult saveBudget(String json) {
        return mvc.put().uri("/api/me/ai-usage/budget").contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }
}
