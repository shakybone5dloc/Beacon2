package dev.beacon.infrastructure.ai;

import com.anthropic.core.JsonValue;
import com.anthropic.models.beta.messages.BetaJsonOutputFormat;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.MessageCreateParams;
import dev.beacon.application.fit.FitScoreException;
import dev.beacon.application.fit.FitScorer;
import dev.beacon.application.providers.ProviderCatalog;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Scores fit with Claude, asking for structured output so the reply always matches the schema. */
@Component
public class ClaudeFitScorer implements FitScorer {

    private final String model;

    public ClaudeFitScorer(@Value("${beacon.ai.anthropic-model:claude-opus-5-5}") String model) {
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
    public FitResult score(FitInput input, String apiKey) {
        var schema = BetaJsonOutputFormat.Schema.builder();
        FitPrompt.SCHEMA.forEach((key, value) -> schema.putAdditionalProperty(key, JsonValue.from(value)));
        var params = ClaudeMessages.withFallback(MessageCreateParams.builder()
                        .model(model)
                        .maxTokens(8000L)
                        .system(FitPrompt.INSTRUCTIONS)
                        // Comparing two documents against a rubric needs little deliberation.
                        .outputConfig(BetaOutputConfig.builder()
                                .effort(BetaOutputConfig.Effort.LOW)
                                .format(BetaJsonOutputFormat.builder().schema(schema.build()).build())
                                .build())
                        .addUserMessage(FitPrompt.userMessage(input)))
                .build();

        var response = ClaudeMessages.create(apiKey, params, FitScoreException::new);
        if (ClaudeMessages.refused(response)) {
            throw new FitScoreException("The AI declined to score this posting.", true);
        }
        return FitPrompt.parse(ClaudeMessages.text(response));
    }
}
