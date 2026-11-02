package dev.beacon.infrastructure.ai;

import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.MessageCreateParams;
import dev.beacon.application.coverletters.CoverLetterException;
import dev.beacon.application.coverletters.CoverLetterWriter;
import dev.beacon.application.providers.ProviderCatalog;
import dev.beacon.application.usage.AiUsageMeter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Drafts cover letters with Claude through the Messages API, using the user's key.
 */
@Component
public class ClaudeCoverLetterWriter implements CoverLetterWriter {

    private final String model;

    public ClaudeCoverLetterWriter(@Value("${beacon.ai.anthropic-model:claude-opus-5-5}") String model) {
        this.model = model;
    }

    @Override
    public String provider() {
        return ProviderCatalog.ANTHROPIC;
    }

    @Override
    public String draft(CoverLetterInput input, String apiKey, AiUsageMeter meter) {
        var params = ClaudeMessages.withFallback(MessageCreateParams.builder()
                        .model(model)
                        .maxTokens(16000L)
                        .system(CoverLetterPrompt.INSTRUCTIONS)
                        // Thinking is always on for this model family; effort sets how hard it works. A letter
                        // grounded in two documents doesn't need more than medium.
                        .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.MEDIUM).build())
                        .addUserMessage(CoverLetterPrompt.userMessage(input)))
                .build();

        var response = ClaudeMessages.create(apiKey, params, CoverLetterException::new);
        meter.record(ClaudeMessages.usage(response));
        if (ClaudeMessages.refused(response)) {
            throw new CoverLetterException("The AI declined to write this letter.", true);
        }
        var text = ClaudeMessages.text(response);
        if (text.isEmpty()) {
            throw new CoverLetterException("The AI returned an empty draft. Try again.", true);
        }
        return text;
    }
}
