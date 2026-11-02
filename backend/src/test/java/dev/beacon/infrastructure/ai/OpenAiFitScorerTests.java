package dev.beacon.infrastructure.ai;

import dev.beacon.application.fit.FitScoreException;
import dev.beacon.application.fit.FitScorer.FitInput;
import dev.beacon.application.usage.AiUsage;
import dev.beacon.application.usage.AiUsageMeter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiFitScorerTests {

    private static final FitInput INPUT =
            new FitInput("Acme", "Java Engineer", "Build services.", "Jordan Smith", LocalDate.of(2026, 11, 1));

    private static final String URL = "https://api.openai.com/v1/chat/completions";

    private record Setup(OpenAiFitScorer scorer, MockRestServiceServer server) {}

    private static Setup setup() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        return new Setup(new OpenAiFitScorer(builder, "gpt-6-sol"), server);
    }

    private static String reply(String content) {
        return """
                {"choices": [{"message": {"role": "assistant", "content": %s, "refusal": null}}]}
                """.formatted(content);
    }

    @Test
    void asksForStrictStructuredOutputAndReadsTheReply() {
        var s = setup();
        s.server().expect(requestTo(URL))
                .andExpect(jsonPath("$.response_format.type").value("json_schema"))
                .andExpect(jsonPath("$.response_format.json_schema.strict").value(true))
                .andExpect(jsonPath("$.response_format.json_schema.schema.required.length()").value(6))
                .andExpect(jsonPath("$.messages[1].content").value(FitPrompt.userMessage(INPUT)))
                .andRespond(withSuccess(reply("""
                        "{\\"score\\": 74, \\"matchedSkills\\": [\\"Java\\", \\" \\", \\"Java\\"], \\"gaps\\": [\\"Go\\"], \\"dealbreakers\\": [], \\"rationale\\": \\" Solid. \\", \\"applyBy\\": \\"2026-11-15\\"}"
                        """), MediaType.APPLICATION_JSON));

        var result = s.scorer().score(INPUT, "sk-test", AiUsageMeter.NONE);

        assertThat(result.score()).isEqualTo(74);
        assertThat(result.matchedSkills()).containsExactly("Java");
        assertThat(result.gaps()).containsExactly("Go");
        assertThat(result.rationale()).isEqualTo("Solid.");
        assertThat(result.applyBy()).isEqualTo(LocalDate.of(2026, 11, 15));
        s.server().verify();
    }

    @Test
    void keepsTheScoreInRangeAndIgnoresAnUnreadableDate() {
        var s = setup();
        s.server().expect(requestTo(URL)).andRespond(withSuccess(reply("""
                "{\\"score\\": 140, \\"matchedSkills\\": [], \\"gaps\\": [], \\"dealbreakers\\": [\\"Secret clearance\\"], \\"rationale\\": \\"Ok\\", \\"applyBy\\": \\"mid-November\\"}"
                """), MediaType.APPLICATION_JSON));

        var result = s.scorer().score(INPUT, "sk-test", AiUsageMeter.NONE);

        assertThat(result.score()).isEqualTo(100);
        assertThat(result.dealbreakers()).containsExactly("Secret clearance");
        assertThat(result.applyBy()).isNull();
    }

    @Test
    void reportsAnUnreadableReplyAndARejectedKey() {
        var s = setup();
        s.server().expect(requestTo(URL)).andRespond(withSuccess(reply("\"not json\""), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> s.scorer().score(INPUT, "sk-test", AiUsageMeter.NONE))
                .isInstanceOf(FitScoreException.class).hasMessageContaining("couldn't read");

        var t = setup();
        t.server().expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON).body("{\"error\": {\"code\": \"invalid_api_key\"}}"));
        assertThatThrownBy(() -> t.scorer().score(INPUT, "sk-test", AiUsageMeter.NONE))
                .isInstanceOf(FitScoreException.class).hasMessageContaining("under Settings");
    }

    @Test
    void reportsTheTokensUsedEvenWhenTheAiDeclines() {
        var s = setup();
        s.server().expect(requestTo(URL)).andRespond(withSuccess("""
                {"choices": [{"message": {"role": "assistant", "content": null, "refusal": "I can't help with that."}}],
                 "usage": {"prompt_tokens": 1200, "completion_tokens": 340,
                           "prompt_tokens_details": {"cached_tokens": 1024},
                           "completion_tokens_details": {"reasoning_tokens": 300}}}
                """, MediaType.APPLICATION_JSON));
        var reported = new ArrayList<AiUsage>();

        assertThatThrownBy(() -> s.scorer().score(INPUT, "sk-test", reported::add))
                .isInstanceOf(FitScoreException.class).hasMessageContaining("declined");

        // Cached tokens are part of prompt_tokens, and reasoning tokens part of completion_tokens.
        assertThat(reported).containsExactly(new AiUsage("openai", "gpt-6-sol", 1200, 1024, 340));
    }

    @Test
    void promptIncludesTodayAndSaysWhenTheEmployerIsNotDisclosed() {
        var message = FitPrompt.userMessage(new FitInput(null, "Engineer", "JD", "CV", LocalDate.of(2026, 11, 1)));

        assertThat(message).startsWith("Today: 2026-11-01").contains("Company: not disclosed").doesNotContain("null");
    }
}
