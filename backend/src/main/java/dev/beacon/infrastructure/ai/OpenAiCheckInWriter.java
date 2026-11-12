package dev.beacon.infrastructure.ai;

import dev.beacon.application.checkin.CheckInException;
import dev.beacon.application.checkin.CheckInWriter;
import dev.beacon.application.providers.ProviderCatalog;
import dev.beacon.application.usage.AiUsageMeter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** Drafts check-in emails with OpenAI, asking for strict structured output so the reply always matches the schema. */
@Component
public class OpenAiCheckInWriter implements CheckInWriter {

    private final OpenAiChat chat;

    @Autowired
    public OpenAiCheckInWriter(@Value("${beacon.ai.openai-model:gpt-6-sol}") String model) {
        this(OpenAiChat.defaultBuilder(), model);
    }

    OpenAiCheckInWriter(RestClient.Builder builder, String model) {
        this.chat = new OpenAiChat(builder, model);
    }

    @Override
    public String provider() {
        return ProviderCatalog.OPENAI;
    }

    @Override
    public Email write(Input input, String apiKey, AiUsageMeter meter) {
        var format = Map.of(
                "type", "json_schema",
                "json_schema", Map.of("name", "check_in_email", "strict", true, "schema", CheckInPrompt.SCHEMA));
        var reply = chat.complete(apiKey, CheckInPrompt.INSTRUCTIONS, CheckInPrompt.userMessage(input),
                Map.of("response_format", format), (message, upstream) -> new CheckInException(message, upstream));
        meter.record(reply.usage());
        if (OpenAiChat.refused(reply.message())) {
            throw new CheckInException("The AI declined to write this email.", true);
        }
        return CheckInPrompt.parse(OpenAiChat.content(reply.message()));
    }
}
