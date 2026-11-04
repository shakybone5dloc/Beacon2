package dev.beacon.infrastructure.ai;

import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.MessageCreateParams;
import dev.beacon.application.briefs.BriefException;
import dev.beacon.application.briefs.BriefWriter;
import dev.beacon.application.providers.ProviderCatalog;
import dev.beacon.application.usage.AiUsageMeter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Writes interview brief sections with Claude through the Messages API, using the user's key. */
@Component
public class ClaudeBriefWriter implements BriefWriter {

    private final String model;

    public ClaudeBriefWriter(@Value("${beacon.ai.anthropic-model:claude-opus-5-5}") String model) {
        this.model = model;
    }

    @Override
    public String provider() {
        return ProviderCatalog.ANTHROPIC;
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public String write(BriefInput input, String apiKey, AiUsageMeter meter) {
        var params = ClaudeMessages.withFallback(MessageCreateParams.builder()
                        .model(model)
                        .maxTokens(16000L)
                        .system(BriefPrompt.INSTRUCTIONS)
                        // Coaching grounded in the documents given; medium effort is plenty.
                        .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.MEDIUM).build())
                        .addUserMessage(BriefPrompt.userMessage(input)))
                .build();

        var response = ClaudeMessages.create(apiKey, params, BriefException::new);
        meter.record(ClaudeMessages.usage(response));
        if (ClaudeMessages.refused(response)) {
            throw new BriefException("The AI declined to write this section.", true);
        }
        var text = ClaudeMessages.text(response);
        if (text.isEmpty()) {
            throw new BriefException("The AI returned an empty section. Try again.", true);
        }
        return text;
    }
}
