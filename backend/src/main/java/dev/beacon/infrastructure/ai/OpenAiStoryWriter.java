package dev.beacon.infrastructure.ai;

import dev.beacon.application.providers.ProviderCatalog;
import dev.beacon.application.stories.StoryException;
import dev.beacon.application.stories.StoryWriter;
import dev.beacon.application.usage.AiUsageMeter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/** Drafts STAR stories with OpenAI, asking for strict structured output so the reply always matches the schema. */
@Component
public class OpenAiStoryWriter implements StoryWriter {

    private final OpenAiChat chat;

    @Autowired
    public OpenAiStoryWriter(@Value("${beacon.ai.openai-model:gpt-6-sol}") String model) {
        this(OpenAiChat.defaultBuilder(), model);
    }

    OpenAiStoryWriter(RestClient.Builder builder, String model) {
        this.chat = new OpenAiChat(builder, model);
    }

    @Override
    public String provider() {
        return ProviderCatalog.OPENAI;
    }

    @Override
    public List<Draft> draft(DraftInput input, String apiKey, AiUsageMeter meter) {
        var format = Map.of(
                "type", "json_schema",
                "json_schema", Map.of("name", "stories", "strict", true, "schema", StoryPrompt.SCHEMA));
        var reply = chat.complete(apiKey, StoryPrompt.INSTRUCTIONS, StoryPrompt.userMessage(input),
                Map.of("response_format", format), StoryException::new);
        meter.record(reply.usage());
        if (OpenAiChat.refused(reply.message())) {
            throw new StoryException("The AI declined to draft these stories.", true);
        }
        return StoryPrompt.parse(OpenAiChat.content(reply.message()));
    }
}
