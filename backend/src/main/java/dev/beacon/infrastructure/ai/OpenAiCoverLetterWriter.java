package dev.beacon.infrastructure.ai;

import dev.beacon.application.coverletters.CoverLetterException;
import dev.beacon.application.coverletters.CoverLetterWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Drafts cover letters with OpenAI's Chat Completions API over plain HTTP.
 * Created by {@link CoverLetterWriterConfig} when OpenAI is the chosen provider.
 */
public class OpenAiCoverLetterWriter implements CoverLetterWriter {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCoverLetterWriter.class);
    private static final Pattern ERROR_CODE = Pattern.compile("\"code\"\\s*:\\s*\"([^\"]+)\"");

    private final RestClient http;
    private final String apiKey;
    private final String model;

    public OpenAiCoverLetterWriter(String apiKey, String model) {
        this(defaultBuilder(), apiKey, model);
    }

    OpenAiCoverLetterWriter(RestClient.Builder builder, String apiKey, String model) {
        this.apiKey = apiKey == null || apiKey.isBlank() ? null : apiKey.strip();
        this.model = model;
        this.http = builder.baseUrl("https://api.openai.com").build();
    }

    /** Drafting can take a while on reasoning models, so reads get a generous timeout. */
    private static RestClient.Builder defaultBuilder() {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofMinutes(3));
        return RestClient.builder().requestFactory(requestFactory);
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null;
    }

    @Override
    public String draft(CoverLetterInput input) {
        if (apiKey == null) {
            throw new CoverLetterException("Set OPENAI_API_KEY or ANTHROPIC_API_KEY in .env to draft cover letters.", false);
        }

        // "developer" is the instructions role for current OpenAI models (it replaced "system").
        var body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "developer", "content", CoverLetterPrompt.INSTRUCTIONS),
                        Map.of("role", "user", "content", CoverLetterPrompt.userMessage(input))));

        JsonNode response;
        try {
            response = http.post()
                    .uri("/v1/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException ex) {
            throw clientError(ex);
        } catch (HttpServerErrorException ex) {
            log.warn("OpenAI returned {}", ex.getStatusCode(), ex);
            throw new CoverLetterException("The AI service returned an error. Try again.", true);
        } catch (ResourceAccessException ex) {
            log.warn("Could not reach OpenAI", ex);
            throw new CoverLetterException("Could not reach the AI service. Check your connection.", true);
        }

        var message = response.path("choices").path(0).path("message");
        var refusal = message.path("refusal");
        if (!refusal.isMissingNode() && !refusal.isNull() && !refusal.asString().isBlank()) {
            throw new CoverLetterException("The AI declined to write this letter.", true);
        }
        var text = message.path("content").isNull() ? "" : message.path("content").asString("").strip();
        if (text.isEmpty()) {
            throw new CoverLetterException("The AI returned an empty draft. Try again.", true);
        }
        return text;
    }

    private CoverLetterException clientError(HttpClientErrorException ex) {
        var status = ex.getStatusCode().value();
        var code = errorCode(ex);
        if (status == 401) {
            return new CoverLetterException("The OpenAI API key was rejected. Check OPENAI_API_KEY in .env.", false);
        }
        if (status == 429 && "insufficient_quota".equals(code)) {
            return new CoverLetterException("Your OpenAI account is out of credits. Add billing at platform.openai.com.", false);
        }
        if (status == 429) {
            return new CoverLetterException("The AI service is busy right now. Try again in a minute.", true);
        }
        if (status == 404 && "model_not_found".equals(code)) {
            return new CoverLetterException("OpenAI doesn't offer the model '" + model
                    + "' to this account. Set OPENAI_MODEL in .env to one it does.", false);
        }
        log.warn("OpenAI rejected the request: {}", ex.getResponseBodyAsString());
        return new CoverLetterException("The AI service rejected the request.", true);
    }

    /** OpenAI errors look like {"error": {"message": ..., "type": ..., "code": ...}}. */
    private static String errorCode(HttpClientErrorException ex) {
        var match = ERROR_CODE.matcher(ex.getResponseBodyAsString());
        return match.find() ? match.group(1) : null;
    }
}
