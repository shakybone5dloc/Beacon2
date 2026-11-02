package dev.beacon.infrastructure.ai;

import dev.beacon.application.fit.FitScoreException;
import dev.beacon.application.fit.FitScorer;
import dev.beacon.application.providers.ProviderCatalog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** Scores fit with OpenAI, asking for strict structured output so the reply always matches the schema. */
@Component
public class OpenAiFitScorer implements FitScorer {

    private final OpenAiChat chat;
    private final String model;

    @Autowired
    public OpenAiFitScorer(@Value("${beacon.ai.openai-model:gpt-6-sol}") String model) {
        this(OpenAiChat.defaultBuilder(), model);
    }

    OpenAiFitScorer(RestClient.Builder builder, String model) {
        this.chat = new OpenAiChat(builder, model);
        this.model = model;
    }

    @Override
    public String provider() {
        return ProviderCatalog.OPENAI;
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public FitResult score(FitInput input, String apiKey) {
        var format = Map.of(
                "type", "json_schema",
                "json_schema", Map.of("name", "fit_score", "strict", true, "schema", FitPrompt.SCHEMA));
        var message = chat.complete(apiKey, FitPrompt.INSTRUCTIONS, FitPrompt.userMessage(input),
                Map.of("response_format", format), FitScoreException::new);
        if (OpenAiChat.refused(message)) {
            throw new FitScoreException("The AI declined to score this posting.", true);
        }
        return FitPrompt.parse(OpenAiChat.content(message));
    }
}
