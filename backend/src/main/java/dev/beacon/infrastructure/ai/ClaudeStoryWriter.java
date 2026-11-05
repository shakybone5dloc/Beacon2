package dev.beacon.infrastructure.ai;

import com.anthropic.core.JsonValue;
import com.anthropic.models.beta.messages.BetaJsonOutputFormat;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.MessageCreateParams;
import dev.beacon.application.providers.ProviderCatalog;
import dev.beacon.application.stories.StoryException;
import dev.beacon.application.stories.StoryWriter;
import dev.beacon.application.usage.AiUsageMeter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/** Drafts STAR stories with Claude, asking for structured output so the reply always matches the schema. */
@Component
public class ClaudeStoryWriter implements StoryWriter {

    private final String model;

    public ClaudeStoryWriter(@Value("${beacon.ai.anthropic-model:claude-opus-5-5}") String model) {
        this.model = model;
    }

    @Override
    public String provider() {
        return ProviderCatalog.ANTHROPIC;
    }

    @Override
    public List<Draft> draft(DraftInput input, String apiKey, AiUsageMeter meter) {
        var schema = BetaJsonOutputFormat.Schema.builder();
        StoryPrompt.SCHEMA.forEach((key, value) -> schema.putAdditionalProperty(key, JsonValue.from(value)));
        var params = ClaudeMessages.withFallback(MessageCreateParams.builder()
                        .model(model)
                        .maxTokens(16000L)
                        .system(StoryPrompt.INSTRUCTIONS)
                        .outputConfig(BetaOutputConfig.builder()
                                .effort(BetaOutputConfig.Effort.MEDIUM)
                                .format(BetaJsonOutputFormat.builder().schema(schema.build()).build())
                                .build())
                        .addUserMessage(StoryPrompt.userMessage(input)))
                .build();

        var response = ClaudeMessages.create(apiKey, params, StoryException::new);
        meter.record(ClaudeMessages.usage(response));
        if (ClaudeMessages.refused(response)) {
            throw new StoryException("The AI declined to draft these stories.", true);
        }
        return StoryPrompt.parse(ClaudeMessages.text(response));
    }
}
