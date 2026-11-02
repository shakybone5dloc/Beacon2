package dev.beacon.infrastructure.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.MessageCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;

/**
 * One call to Claude's Messages API with the user's key, with its failures turned
 * into messages a user can act on. Shared by every Claude-backed feature. The client
 * is built per call; calls are rare enough that this costs nothing.
 */
final class ClaudeMessages {

    private static final Logger log = LoggerFactory.getLogger(ClaudeMessages.class);

    /** Server-side refusal fallback: if a safety classifier declines, the API retries on another model. */
    private static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

    private ClaudeMessages() {}

    /** Adds the refusal fallback to a request. */
    static MessageCreateParams.Builder withFallback(MessageCreateParams.Builder params) {
        return params.addBeta(FALLBACK_BETA).putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
    }

    static BetaMessage create(String apiKey, MessageCreateParams params, AiErrors errors) {
        AnthropicClient client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
        try {
            return client.beta().messages().create(params);
        } catch (UnauthorizedException ex) {
            throw errors.create("Anthropic rejected your API key. Check it under Settings.", false);
        } catch (BadRequestException ex) {
            log.warn("Claude request rejected", ex);
            throw errors.create("The AI service rejected the request: " + ex.getMessage(), true);
        } catch (RateLimitException ex) {
            throw errors.create("The AI service is busy right now. Try again in a minute.", true);
        } catch (AnthropicServiceException ex) {
            log.warn("Claude request failed", ex);
            throw errors.create("The AI service returned an error. Try again.", true);
        } catch (AnthropicIoException ex) {
            log.warn("Could not reach Claude", ex);
            throw errors.create("Could not reach the AI service. Check your connection.", true);
        } finally {
            client.close();
        }
    }

    static boolean refused(BetaMessage response) {
        return response.stopReason().map(reason -> reason.equals(BetaStopReason.REFUSAL)).orElse(false);
    }

    /** The reply's text blocks joined and stripped; empty when there are none. */
    static String text(BetaMessage response) {
        return response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(block -> block.text())
                .collect(Collectors.joining("\n"))
                .strip();
    }
}
