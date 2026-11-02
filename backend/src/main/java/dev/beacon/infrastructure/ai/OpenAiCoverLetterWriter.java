package dev.beacon.infrastructure.ai;

import dev.beacon.application.coverletters.CoverLetterException;
import dev.beacon.application.coverletters.CoverLetterWriter;
import dev.beacon.application.providers.ProviderCatalog;
import dev.beacon.application.usage.AiUsageMeter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Drafts cover letters with OpenAI's Chat Completions API over plain HTTP.
 */
@Component
public class OpenAiCoverLetterWriter implements CoverLetterWriter {

    private final OpenAiChat chat;

    @Autowired
    public OpenAiCoverLetterWriter(@Value("${beacon.ai.openai-model:gpt-6-sol}") String model) {
        this(OpenAiChat.defaultBuilder(), model);
    }

    OpenAiCoverLetterWriter(RestClient.Builder builder, String model) {
        this.chat = new OpenAiChat(builder, model);
    }

    @Override
    public String provider() {
        return ProviderCatalog.OPENAI;
    }

    @Override
    public String draft(CoverLetterInput input, String apiKey, AiUsageMeter meter) {
        var reply = chat.complete(apiKey, CoverLetterPrompt.INSTRUCTIONS, CoverLetterPrompt.userMessage(input),
                Map.of(), CoverLetterException::new);
        meter.record(reply.usage());
        var message = reply.message();
        if (OpenAiChat.refused(message)) {
            throw new CoverLetterException("The AI declined to write this letter.", true);
        }
        var text = OpenAiChat.content(message);
        if (text.isEmpty()) {
            throw new CoverLetterException("The AI returned an empty draft. Try again.", true);
        }
        return text;
    }
}
