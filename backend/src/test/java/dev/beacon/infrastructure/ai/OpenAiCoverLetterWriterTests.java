package dev.beacon.infrastructure.ai;

import dev.beacon.application.coverletters.CoverLetterException;
import dev.beacon.application.coverletters.CoverLetterWriter.CoverLetterInput;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiCoverLetterWriterTests {

    private static final CoverLetterInput INPUT =
            new CoverLetterInput("Acme", "Java Engineer", "Build services.", "Jordan Smith", null);

    private static final String URL = "https://api.openai.com/v1/chat/completions";

    private record Setup(OpenAiCoverLetterWriter writer, MockRestServiceServer server) {}

    private static Setup setup() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        return new Setup(new OpenAiCoverLetterWriter(builder, "gpt-6-sol"), server);
    }

    private static String error(String code) {
        return "{\"error\": {\"message\": \"nope\", \"type\": \"" + code + "\", \"code\": \"" + code + "\"}}";
    }

    @Test
    void sendsTheInstructionsAndDocumentsAndReturnsTheLetter() {
        var s = setup();
        s.server().expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer sk-test"))
                .andExpect(jsonPath("$.model").value("gpt-6-sol"))
                .andExpect(jsonPath("$.messages[0].role").value("developer"))
                .andExpect(jsonPath("$.messages[0].content").value(CoverLetterPrompt.INSTRUCTIONS))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value(CoverLetterPrompt.userMessage(INPUT)))
                .andRespond(withSuccess("""
                        {"choices": [{"index": 0, "finish_reason": "stop",
                          "message": {"role": "assistant", "content": "  Dear Hiring Team,\\n\\nHello.  ", "refusal": null}}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(s.writer().draft(INPUT, "sk-test")).isEqualTo("Dear Hiring Team,\n\nHello.");
        s.server().verify();
    }

    @Test
    void explainsAnAccountWithoutCredits() {
        var s = setup();
        s.server().expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON).body(error("insufficient_quota")));

        assertThatThrownBy(() -> s.writer().draft(INPUT, "sk-test"))
                .isInstanceOf(CoverLetterException.class)
                .hasMessageContaining("out of credits")
                .satisfies(ex -> assertThat(((CoverLetterException) ex).isUpstream()).isFalse());
    }

    @Test
    void treatsAPlainRateLimitAsRetryable() {
        var s = setup();
        s.server().expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON).body(error("rate_limit_exceeded")));

        assertThatThrownBy(() -> s.writer().draft(INPUT, "sk-test"))
                .hasMessageContaining("busy")
                .satisfies(ex -> assertThat(((CoverLetterException) ex).isUpstream()).isTrue());
    }

    @Test
    void explainsARejectedKeyAndAnUnknownModel() {
        var s = setup();
        s.server().expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON).body(error("invalid_api_key")));
        assertThatThrownBy(() -> s.writer().draft(INPUT, "sk-test")).hasMessageContaining("under Settings");

        var t = setup();
        t.server().expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON).body(error("model_not_found")));
        assertThatThrownBy(() -> t.writer().draft(INPUT, "sk-test")).hasMessageContaining("OPENAI_MODEL");
    }

    @Test
    void reportsARefusal() {
        var s = setup();
        s.server().expect(requestTo(URL)).andRespond(withSuccess("""
                {"choices": [{"message": {"role": "assistant", "content": null, "refusal": "I can't help with that."}}]}
                """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> s.writer().draft(INPUT, "sk-test")).hasMessageContaining("declined");
    }
}
