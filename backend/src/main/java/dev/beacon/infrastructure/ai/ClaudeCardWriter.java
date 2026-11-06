package dev.beacon.infrastructure.ai;

import com.anthropic.core.JsonValue;
import com.anthropic.models.beta.messages.BetaJsonOutputFormat;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.MessageCreateParams;
import dev.beacon.application.providers.ProviderCatalog;
import dev.beacon.application.study.CardWriter;
import dev.beacon.application.study.StudyException;
import dev.beacon.application.usage.AiUsageMeter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/** Makes study cards with Claude, asking for structured output so the reply always matches the schema. */
@Component
public class ClaudeCardWriter implements CardWriter {

    private final String model;

    public ClaudeCardWriter(@Value("${beacon.ai.anthropic-model:claude-opus-5-5}") String model) {
        this.model = model;
    }

    @Override
    public String provider() {
        return ProviderCatalog.ANTHROPIC;
    }

    @Override
    public List<CardDraft> write(CardInput input, String apiKey, AiUsageMeter meter) {
        var schema = BetaJsonOutputFormat.Schema.builder();
        CardPrompt.SCHEMA.forEach((key, value) -> schema.putAdditionalProperty(key, JsonValue.from(value)));
        var params = ClaudeMessages.withFallback(MessageCreateParams.builder()
                        .model(model)
                        .maxTokens(16000L)
                        .system(CardPrompt.INSTRUCTIONS)
                        // Restating what the brief already says needs little deliberation.
                        .outputConfig(BetaOutputConfig.builder()
                                .effort(BetaOutputConfig.Effort.LOW)
                                .format(BetaJsonOutputFormat.builder().schema(schema.build()).build())
                                .build())
                        .addUserMessage(CardPrompt.userMessage(input)))
                .build();

        var response = ClaudeMessages.create(apiKey, params, (message, upstream) -> new StudyException(message, upstream));
        meter.record(ClaudeMessages.usage(response));
        if (ClaudeMessages.refused(response)) {
            throw new StudyException("The AI declined to make these cards.", true);
        }
        return CardPrompt.parse(ClaudeMessages.text(response));
    }
}
