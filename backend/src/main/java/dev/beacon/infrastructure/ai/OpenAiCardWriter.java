package dev.beacon.infrastructure.ai;

import dev.beacon.application.providers.ProviderCatalog;
import dev.beacon.application.study.CardWriter;
import dev.beacon.application.study.StudyException;
import dev.beacon.application.usage.AiUsageMeter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/** Makes study cards with OpenAI, asking for strict structured output so the reply always matches the schema. */
@Component
public class OpenAiCardWriter implements CardWriter {

    private final OpenAiChat chat;

    @Autowired
    public OpenAiCardWriter(@Value("${beacon.ai.openai-model:gpt-6-sol}") String model) {
        this(OpenAiChat.defaultBuilder(), model);
    }

    OpenAiCardWriter(RestClient.Builder builder, String model) {
        this.chat = new OpenAiChat(builder, model);
    }

    @Override
    public String provider() {
        return ProviderCatalog.OPENAI;
    }

    @Override
    public List<CardDraft> write(CardInput input, String apiKey, AiUsageMeter meter) {
        var format = Map.of(
                "type", "json_schema",
                "json_schema", Map.of("name", "study_cards", "strict", true, "schema", CardPrompt.SCHEMA));
        var reply = chat.complete(apiKey, CardPrompt.INSTRUCTIONS, CardPrompt.userMessage(input),
                Map.of("response_format", format), (message, upstream) -> new StudyException(message, upstream));
        meter.record(reply.usage());
        if (OpenAiChat.refused(reply.message())) {
            throw new StudyException("The AI declined to make these cards.", true);
        }
        return CardPrompt.parse(OpenAiChat.content(reply.message()));
    }
}
