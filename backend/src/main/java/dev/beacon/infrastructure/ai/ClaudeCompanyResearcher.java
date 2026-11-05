package dev.beacon.infrastructure.ai;

import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaWebSearchTool20260209;
import com.anthropic.models.beta.messages.MessageCreateParams;
import dev.beacon.application.briefs.BriefException;
import dev.beacon.application.briefs.BriefWriter.BriefInput;
import dev.beacon.application.briefs.CompanyResearcher;
import dev.beacon.application.providers.ProviderCatalog;
import dev.beacon.application.usage.AiUsageMeter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Researches the employer with Claude and its server-side web search. Claude runs the
 * searches itself and returns its text with citations to the pages it used.
 */
@Component
public class ClaudeCompanyResearcher implements CompanyResearcher {

    /** Searches per research: plenty for one company, and a cap on what it can cost. */
    static final long MAX_SEARCHES = 6;
    /** A long research can pause mid-turn; it is sent back to carry on this many times at most. */
    private static final int MAX_CONTINUATIONS = 2;

    private final String model;

    public ClaudeCompanyResearcher(@Value("${beacon.ai.anthropic-model:claude-opus-5-5}") String model) {
        this.model = model;
    }

    @Override
    public String provider() {
        return ProviderCatalog.ANTHROPIC;
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public String research(BriefInput input, String apiKey, AiUsageMeter meter) {
        var userMessage = BriefPrompt.userMessage(input);
        var replies = new ArrayList<BetaMessage>();
        BetaMessage response;
        do {
            var params = ClaudeMessages.withFallback(MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(16000L)
                    .system(BriefPrompt.INSTRUCTIONS)
                    .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.MEDIUM).build())
                    .addTool(BetaWebSearchTool20260209.builder().maxUses(MAX_SEARCHES).build())
                    .addUserMessage(userMessage));
            // After a pause, the reply so far goes back unchanged and Claude carries on from it.
            replies.forEach(params::addMessage);
            response = ClaudeMessages.create(apiKey, params.build(), BriefException::new);
            meter.record(ClaudeMessages.usage(response));
            if (ClaudeMessages.refused(response)) {
                throw new BriefException("The AI declined to research this company.", true);
            }
            replies.add(response);
        } while (paused(response) && replies.size() <= MAX_CONTINUATIONS);

        return ResearchMarkdown.render(cited(replies));
    }

    private static boolean paused(BetaMessage response) {
        return response.stopReason().map(BetaStopReason.PAUSE_TURN::equals).orElse(false);
    }

    /** Each text block, with the pages its citations point to; the search results themselves are left out. */
    static List<ResearchMarkdown.Cited> cited(List<BetaMessage> replies) {
        var parts = new ArrayList<ResearchMarkdown.Cited>();
        for (var reply : replies) {
            for (var block : reply.content()) {
                block.text().ifPresent(text -> parts.add(new ResearchMarkdown.Cited(text.text(),
                        text.citations().orElse(List.of()).stream()
                                .flatMap(citation -> citation.webSearchResultLocation().stream())
                                .map(location -> new ResearchMarkdown.Source(location.title().orElse(null), location.url()))
                                .toList())));
            }
        }
        return parts;
    }
}
