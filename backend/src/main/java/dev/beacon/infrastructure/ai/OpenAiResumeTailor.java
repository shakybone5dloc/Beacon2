package dev.beacon.infrastructure.ai;

import dev.beacon.application.providers.ProviderCatalog;
import dev.beacon.application.tailoring.ResumeTailor;
import dev.beacon.application.tailoring.TailoringException;
import dev.beacon.application.usage.AiUsageMeter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/** Suggests resume edits with OpenAI, asking for strict structured output so the reply always matches the schema. */
@Component
public class OpenAiResumeTailor implements ResumeTailor {

    private final OpenAiChat chat;
    private final String model;

    @Autowired
    public OpenAiResumeTailor(@Value("${beacon.ai.openai-model:gpt-6-sol}") String model) {
        this(OpenAiChat.defaultBuilder(), model);
    }

    OpenAiResumeTailor(RestClient.Builder builder, String model) {
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
    public List<Suggestion> suggest(TailorInput input, String apiKey, AiUsageMeter meter) {
        var format = Map.of(
                "type", "json_schema",
                "json_schema", Map.of("name", "resume_suggestions", "strict", true, "schema", TailorPrompt.SCHEMA));
        var reply = chat.complete(apiKey, TailorPrompt.INSTRUCTIONS, TailorPrompt.userMessage(input),
                Map.of("response_format", format), TailoringException::new);
        meter.record(reply.usage());
        if (OpenAiChat.refused(reply.message())) {
            throw new TailoringException("The AI declined to suggest changes to this resume.", true);
        }
        return TailorPrompt.parse(OpenAiChat.content(reply.message()));
    }
}
