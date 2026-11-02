package dev.beacon.infrastructure.ai;

import dev.beacon.application.providers.ProviderCatalog;
import dev.beacon.application.usage.AiUsage;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * One call to OpenAI's Chat Completions API over plain HTTP, with its failures
 * turned into messages a user can act on. Shared by every OpenAI-backed feature.
 */
final class OpenAiChat {

    private static final Logger log = LoggerFactory.getLogger(OpenAiChat.class);
    private static final Pattern ERROR_CODE = Pattern.compile("\"code\"\\s*:\\s*\"([^\"]+)\"");

    private final RestClient http;
    private final String model;

    OpenAiChat(RestClient.Builder builder, String model) {
        this.model = model;
        this.http = builder.baseUrl("https://api.openai.com").build();
    }

    /** Reasoning models can take a while, so reads get a generous timeout. */
    static RestClient.Builder defaultBuilder() {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofMinutes(3));
        return RestClient.builder().requestFactory(requestFactory);
    }

    /** The reply's {@code message} node, and the tokens the call used. */
    record Reply(JsonNode message, AiUsage usage) {}

    /**
     * Sends the instructions and the user's message and returns the reply.
     * {@code extra} adds request fields such as {@code response_format}.
     */
    Reply complete(String apiKey, String instructions, String userMessage, Map<String, Object> extra, AiErrors errors) {
        var body = new LinkedHashMap<String, Object>();
        body.put("model", model);
        // "developer" is the instructions role for current OpenAI models (it replaced "system").
        body.put("messages", List.of(
                Map.of("role", "developer", "content", instructions),
                Map.of("role", "user", "content", userMessage)));
        body.putAll(extra);

        try {
            var response = http.post()
                    .uri("/v1/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            return new Reply(response.path("choices").path(0).path("message"), usage(response.path("usage")));
        } catch (HttpClientErrorException ex) {
            throw clientError(ex, errors);
        } catch (HttpServerErrorException ex) {
            log.warn("OpenAI returned {}", ex.getStatusCode(), ex);
            throw errors.create("The AI service returned an error. Try again.", true);
        } catch (ResourceAccessException ex) {
            log.warn("Could not reach OpenAI", ex);
            throw errors.create("Could not reach the AI service. Check your connection.", true);
        }
    }

    /**
     * OpenAI counts cached tokens inside {@code prompt_tokens} and reasoning tokens inside
     * {@code completion_tokens}, which matches how {@link AiUsage} counts them.
     */
    private AiUsage usage(JsonNode usage) {
        return new AiUsage(ProviderCatalog.OPENAI, model,
                usage.path("prompt_tokens").asLong(0),
                usage.path("prompt_tokens_details").path("cached_tokens").asLong(0),
                usage.path("completion_tokens").asLong(0));
    }

    /** True when the model declined to answer. */
    static boolean refused(JsonNode message) {
        var refusal = message.path("refusal");
        return !refusal.isMissingNode() && !refusal.isNull() && !refusal.asString().isBlank();
    }

    /** The reply's text, stripped; empty when there is none. */
    static String content(JsonNode message) {
        return message.path("content").isNull() ? "" : message.path("content").asString("").strip();
    }

    private RuntimeException clientError(HttpClientErrorException ex, AiErrors errors) {
        var status = ex.getStatusCode().value();
        var code = errorCode(ex);
        if (status == 401) {
            return errors.create("OpenAI rejected your API key. Check it under Settings.", false);
        }
        if (status == 429 && "insufficient_quota".equals(code)) {
            return errors.create("Your OpenAI account is out of credits. Add billing at platform.openai.com.", false);
        }
        if (status == 429) {
            return errors.create("The AI service is busy right now. Try again in a minute.", true);
        }
        if (status == 404 && "model_not_found".equals(code)) {
            return errors.create("OpenAI doesn't offer the model '" + model
                    + "' to this account. Ask whoever runs Beacon to set OPENAI_MODEL to one it does.", false);
        }
        log.warn("OpenAI rejected the request: {}", ex.getResponseBodyAsString());
        return errors.create("The AI service rejected the request.", true);
    }

    /** OpenAI errors look like {"error": {"message": ..., "type": ..., "code": ...}}. */
    private static String errorCode(HttpClientErrorException ex) {
        var match = ERROR_CODE.matcher(ex.getResponseBodyAsString());
        return match.find() ? match.group(1) : null;
    }
}
