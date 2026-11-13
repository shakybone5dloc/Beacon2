package dev.beacon.infrastructure.ai;

import com.anthropic.core.JsonValue;
import com.anthropic.models.beta.messages.BetaJsonOutputFormat;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.MessageCreateParams;
import dev.beacon.application.providers.ProviderCatalog;
import dev.beacon.application.study.AnswerGrader;
import dev.beacon.application.study.StudyException;
import dev.beacon.application.usage.AiUsageMeter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Checks recall answers with Claude, asking for structured output so the reply always matches the schema. */
@Component
public class ClaudeAnswerGrader implements AnswerGrader {

    private final String model;

    public ClaudeAnswerGrader(@Value("${beacon.ai.anthropic-model:claude-opus-5-5}") String model) {
        this.model = model;
    }

    @Override
    public String provider() {
        return ProviderCatalog.ANTHROPIC;
    }

    @Override
    public Verdict grade(Input input, String apiKey, AiUsageMeter meter) {
        var schema = BetaJsonOutputFormat.Schema.builder();
        GradePrompt.SCHEMA.forEach((key, value) -> schema.putAdditionalProperty(key, JsonValue.from(value)));
        var params = ClaudeMessages.withFallback(MessageCreateParams.builder()
                        .model(model)
                        .maxTokens(4000L)
                        .system(GradePrompt.INSTRUCTIONS)
                        // Comparing two short answers needs little deliberation, and the user is waiting.
                        .outputConfig(BetaOutputConfig.builder()
                                .effort(BetaOutputConfig.Effort.LOW)
                                .format(BetaJsonOutputFormat.builder().schema(schema.build()).build())
                                .build())
                        .addUserMessage(GradePrompt.userMessage(input)))
                .build();

        var response = ClaudeMessages.create(apiKey, params, (message, upstream) -> new StudyException(message, upstream));
        meter.record(ClaudeMessages.usage(response));
        if (ClaudeMessages.refused(response)) {
            throw new StudyException("The AI declined to check this answer.", true);
        }
        return GradePrompt.parse(ClaudeMessages.text(response));
    }
}
