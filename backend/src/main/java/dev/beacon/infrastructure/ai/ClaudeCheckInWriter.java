package dev.beacon.infrastructure.ai;

import com.anthropic.core.JsonValue;
import com.anthropic.models.beta.messages.BetaJsonOutputFormat;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.MessageCreateParams;
import dev.beacon.application.checkin.CheckInException;
import dev.beacon.application.checkin.CheckInWriter;
import dev.beacon.application.providers.ProviderCatalog;
import dev.beacon.application.usage.AiUsageMeter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Drafts check-in emails with Claude, asking for structured output so the reply always matches the schema. */
@Component
public class ClaudeCheckInWriter implements CheckInWriter {

    private final String model;

    public ClaudeCheckInWriter(@Value("${beacon.ai.anthropic-model:claude-opus-5-5}") String model) {
        this.model = model;
    }

    @Override
    public String provider() {
        return ProviderCatalog.ANTHROPIC;
    }

    @Override
    public Email write(Input input, String apiKey, AiUsageMeter meter) {
        var schema = BetaJsonOutputFormat.Schema.builder();
        CheckInPrompt.SCHEMA.forEach((key, value) -> schema.putAdditionalProperty(key, JsonValue.from(value)));
        var params = ClaudeMessages.withFallback(MessageCreateParams.builder()
                        .model(model)
                        .maxTokens(4000L)
                        .system(CheckInPrompt.INSTRUCTIONS)
                        // A few sentences from facts already given need little deliberation.
                        .outputConfig(BetaOutputConfig.builder()
                                .effort(BetaOutputConfig.Effort.LOW)
                                .format(BetaJsonOutputFormat.builder().schema(schema.build()).build())
                                .build())
                        .addUserMessage(CheckInPrompt.userMessage(input)))
                .build();

        var response = ClaudeMessages.create(apiKey, params, (message, upstream) -> new CheckInException(message, upstream));
        meter.record(ClaudeMessages.usage(response));
        if (ClaudeMessages.refused(response)) {
            throw new CheckInException("The AI declined to write this email.", true);
        }
        return CheckInPrompt.parse(ClaudeMessages.text(response));
    }
}
