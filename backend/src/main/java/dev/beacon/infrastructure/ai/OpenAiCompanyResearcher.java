package dev.beacon.infrastructure.ai;

import dev.beacon.application.briefs.BriefException;
import dev.beacon.application.briefs.BriefWriter.BriefInput;
import dev.beacon.application.briefs.CompanyResearcher;
import dev.beacon.application.providers.ProviderCatalog;
import dev.beacon.application.usage.AiUsageMeter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Researches the employer with OpenAI's Responses API and its web search tool. The answer
 * comes back with {@code url_citation} annotations: character ranges of the text and their pages.
 */
@Component
public class OpenAiCompanyResearcher implements CompanyResearcher {

    private final OpenAiChat chat;
    private final String model;

    @Autowired
    public OpenAiCompanyResearcher(@Value("${beacon.ai.openai-model:gpt-6-sol}") String model) {
        this(OpenAiChat.defaultBuilder(), model);
    }

    OpenAiCompanyResearcher(RestClient.Builder builder, String model) {
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
    public String research(BriefInput input, String apiKey, AiUsageMeter meter) {
        var reply = chat.respond(apiKey, BriefPrompt.INSTRUCTIONS, BriefPrompt.userMessage(input),
                Map.of("tools", List.of(Map.of("type", "web_search"))), BriefException::new);
        meter.record(reply.usage());

        var parts = new ArrayList<ResearchMarkdown.Cited>();
        for (var item : reply.body().path("output").values()) {
            if (!"message".equals(item.path("type").asString(""))) continue;
            for (var content : item.path("content").values()) {
                var type = content.path("type").asString("");
                if ("refusal".equals(type)) {
                    throw new BriefException("The AI declined to research this company.", true);
                }
                if ("output_text".equals(type)) parts.addAll(cited(content));
            }
        }
        if (parts.stream().allMatch(p -> p.text().isBlank())) {
            throw new BriefException("The AI returned no research. Try again.", true);
        }
        return ResearchMarkdown.render(parts);
    }

    /**
     * Splits one output text at the ends of its cited ranges. OpenAI counts characters as code
     * points, while Java strings count UTF-16 units, so the indexes are converted.
     */
    static List<ResearchMarkdown.Cited> cited(JsonNode content) {
        var text = content.path("text").asString("");
        var codePoints = text.codePointCount(0, text.length());
        var ranges = new ArrayList<ResearchMarkdown.Range>();
        for (var annotation : content.path("annotations").values()) {
            if (!"url_citation".equals(annotation.path("type").asString(""))) continue;
            var end = Math.min(Math.max(annotation.path("end_index").asInt(0), 0), codePoints);
            ranges.add(new ResearchMarkdown.Range(text.offsetByCodePoints(0, end), new ResearchMarkdown.Source(
                    textOf(annotation, "title"), textOf(annotation, "url"))));
        }
        return ResearchMarkdown.fromRanges(text, ranges);
    }

    private static String textOf(JsonNode node, String field) {
        var value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asString().strip();
    }
}
