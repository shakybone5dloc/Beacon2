package dev.beacon.infrastructure.ai;

import dev.beacon.application.briefs.BriefException;
import dev.beacon.application.briefs.BriefWriter;
import dev.beacon.application.providers.ProviderCatalog;
import dev.beacon.application.usage.AiUsageMeter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** Writes interview brief sections with OpenAI's Chat Completions API over plain HTTP. */
@Component
public class OpenAiBriefWriter implements BriefWriter {

    private final OpenAiChat chat;
    private final String model;

    @Autowired
    public OpenAiBriefWriter(@Value("${beacon.ai.openai-model:gpt-6-sol}") String model) {
        this(OpenAiChat.defaultBuilder(), model);
    }

    OpenAiBriefWriter(RestClient.Builder builder, String model) {
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
    public String write(BriefInput input, String apiKey, AiUsageMeter meter) {
        var reply = chat.complete(apiKey, BriefPrompt.INSTRUCTIONS, BriefPrompt.userMessage(input), Map.of(),
                BriefException::new);
        meter.record(reply.usage());
        var message = reply.message();
        if (OpenAiChat.refused(message)) {
            throw new BriefException("The AI declined to write this section.", true);
        }
        var text = OpenAiChat.content(message);
        if (text.isEmpty()) {
            throw new BriefException("The AI returned an empty section. Try again.", true);
        }
        return text;
    }
}
