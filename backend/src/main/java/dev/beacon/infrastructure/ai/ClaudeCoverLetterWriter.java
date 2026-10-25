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
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.MessageCreateParams;
import dev.beacon.application.coverletters.CoverLetterException;
import dev.beacon.application.coverletters.CoverLetterWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;

/**
 * Drafts cover letters with Claude through the Messages API. Created by
 * {@link CoverLetterWriterConfig} when Claude is the chosen provider.
 */
public class ClaudeCoverLetterWriter implements CoverLetterWriter {

    private static final Logger log = LoggerFactory.getLogger(ClaudeCoverLetterWriter.class);

    /** Server-side refusal fallback: if a safety classifier declines, the API retries on another model. */
    private static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

    private final AnthropicClient client;
    private final String model;

    public ClaudeCoverLetterWriter(String apiKey, String model) {
        this.client = apiKey == null || apiKey.isBlank()
                ? null
                : AnthropicOkHttpClient.builder().apiKey(apiKey.strip()).build();
        this.model = model;
    }

    @Override
    public boolean isConfigured() {
        return client != null;
    }

    @Override
    public String draft(CoverLetterInput input) {
        if (client == null) {
            throw new CoverLetterException("Set OPENAI_API_KEY or ANTHROPIC_API_KEY in .env to draft cover letters.", false);
        }

        var params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                .system(CoverLetterPrompt.INSTRUCTIONS)
                // Thinking is always on for this model family; effort sets how hard it works. A letter
                // grounded in two documents doesn't need more than medium.
                .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.MEDIUM).build())
                .addBeta(FALLBACK_BETA)
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .addUserMessage(CoverLetterPrompt.userMessage(input))
                .build();

        BetaMessage response;
        try {
            response = client.beta().messages().create(params);
        } catch (UnauthorizedException ex) {
            throw new CoverLetterException("The Anthropic API key was rejected. Check ANTHROPIC_API_KEY in .env.", false);
        } catch (BadRequestException ex) {
            log.warn("Cover letter request rejected", ex);
            throw new CoverLetterException("The AI service rejected the request: " + ex.getMessage(), true);
        } catch (RateLimitException ex) {
            throw new CoverLetterException("The AI service is busy right now. Try again in a minute.", true);
        } catch (AnthropicServiceException ex) {
            log.warn("Cover letter request failed", ex);
            throw new CoverLetterException("The AI service returned an error. Try again.", true);
        } catch (AnthropicIoException ex) {
            log.warn("Could not reach the AI service", ex);
            throw new CoverLetterException("Could not reach the AI service. Check your connection.", true);
        }

        if (response.stopReason().map(reason -> reason.equals(BetaStopReason.REFUSAL)).orElse(false)) {
            throw new CoverLetterException("The AI declined to write this letter.", true);
        }

        var text = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(block -> block.text())
                .collect(Collectors.joining("\n"))
                .strip();
        if (text.isEmpty()) {
            throw new CoverLetterException("The AI returned an empty draft. Try again.", true);
        }
        return text;
    }
}
