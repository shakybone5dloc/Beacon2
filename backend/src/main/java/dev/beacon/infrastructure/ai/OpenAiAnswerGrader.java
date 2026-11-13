package dev.beacon.infrastructure.ai;

import dev.beacon.application.providers.ProviderCatalog;
import dev.beacon.application.study.AnswerGrader;
import dev.beacon.application.study.StudyException;
import dev.beacon.application.usage.AiUsageMeter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** Checks recall answers with OpenAI, asking for strict structured output so the reply always matches the schema. */
@Component
public class OpenAiAnswerGrader implements AnswerGrader {

    private final OpenAiChat chat;

    @Autowired
    public OpenAiAnswerGrader(@Value("${beacon.ai.openai-model:gpt-6-sol}") String model) {
        this(OpenAiChat.defaultBuilder(), model);
    }

    OpenAiAnswerGrader(RestClient.Builder builder, String model) {
        this.chat = new OpenAiChat(builder, model);
    }

    @Override
    public String provider() {
        return ProviderCatalog.OPENAI;
    }

    @Override
    public Verdict grade(Input input, String apiKey, AiUsageMeter meter) {
        var format = Map.of(
                "type", "json_schema",
                "json_schema", Map.of("name", "recall_grade", "strict", true, "schema", GradePrompt.SCHEMA));
        var reply = chat.complete(apiKey, GradePrompt.INSTRUCTIONS, GradePrompt.userMessage(input),
                Map.of("response_format", format), (message, upstream) -> new StudyException(message, upstream));
        meter.record(reply.usage());
        if (OpenAiChat.refused(reply.message())) {
            throw new StudyException("The AI declined to check this answer.", true);
        }
        return GradePrompt.parse(OpenAiChat.content(reply.message()));
    }
}
