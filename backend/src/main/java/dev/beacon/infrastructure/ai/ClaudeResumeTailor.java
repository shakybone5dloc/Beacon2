package dev.beacon.infrastructure.ai;

import com.anthropic.core.JsonValue;
import com.anthropic.models.beta.messages.BetaJsonOutputFormat;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.MessageCreateParams;
import dev.beacon.application.providers.ProviderCatalog;
import dev.beacon.application.tailoring.ResumeTailor;
import dev.beacon.application.tailoring.TailoringException;
import dev.beacon.application.usage.AiUsageMeter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/** Suggests resume edits with Claude, asking for structured output so the reply always matches the schema. */
@Component
public class ClaudeResumeTailor implements ResumeTailor {

    private final String model;

    public ClaudeResumeTailor(@Value("${beacon.ai.anthropic-model:claude-opus-5-5}") String model) {
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
    public List<Suggestion> suggest(TailorInput input, String apiKey, AiUsageMeter meter) {
        var schema = BetaJsonOutputFormat.Schema.builder();
        TailorPrompt.SCHEMA.forEach((key, value) -> schema.putAdditionalProperty(key, JsonValue.from(value)));
        var params = ClaudeMessages.withFallback(MessageCreateParams.builder()
                        .model(model)
                        .maxTokens(16000L)
                        .system(TailorPrompt.INSTRUCTIONS)
                        .outputConfig(BetaOutputConfig.builder()
                                .effort(BetaOutputConfig.Effort.MEDIUM)
                                .format(BetaJsonOutputFormat.builder().schema(schema.build()).build())
                                .build())
                        .addUserMessage(TailorPrompt.userMessage(input)))
                .build();

        var response = ClaudeMessages.create(apiKey, params, TailoringException::new);
        meter.record(ClaudeMessages.usage(response));
        if (ClaudeMessages.refused(response)) {
            throw new TailoringException("The AI declined to suggest changes to this resume.", true);
        }
        return TailorPrompt.parse(ClaudeMessages.text(response));
    }
}
