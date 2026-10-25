package dev.beacon.infrastructure.ai;

import dev.beacon.application.coverletters.CoverLetterWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;

/**
 * Chooses the AI provider for cover letters. With BEACON_AI_PROVIDER unset
 * ("auto"), OpenAI is used when its key is present, otherwise Claude.
 */
@Configuration
public class CoverLetterWriterConfig {

    private static final Logger log = LoggerFactory.getLogger(CoverLetterWriterConfig.class);

    @Bean
    CoverLetterWriter coverLetterWriter(
            @Value("${beacon.ai.provider:auto}") String provider,
            @Value("${beacon.ai.openai-api-key:}") String openAiKey,
            @Value("${beacon.ai.openai-model:gpt-6-sol}") String openAiModel,
            @Value("${beacon.ai.anthropic-api-key:}") String anthropicKey,
            @Value("${beacon.ai.anthropic-model:claude-opus-5-5}") String anthropicModel
    ) {
        var choice = choose(provider, hasText(openAiKey), hasText(anthropicKey));
        log.info("Cover letters will be drafted with {}", choice == Provider.OPENAI ? "OpenAI (" + openAiModel + ")"
                : "Claude (" + anthropicModel + ")");
        return choice == Provider.OPENAI
                ? new OpenAiCoverLetterWriter(openAiKey, openAiModel)
                : new ClaudeCoverLetterWriter(anthropicKey, anthropicModel);
    }

    enum Provider { OPENAI, ANTHROPIC }

    /** An explicit provider wins; "auto" prefers whichever key is set, OpenAI first. */
    static Provider choose(String provider, boolean hasOpenAiKey, boolean hasAnthropicKey) {
        return switch (provider == null ? "auto" : provider.strip().toLowerCase(Locale.ROOT)) {
            case "openai" -> Provider.OPENAI;
            case "anthropic", "claude" -> Provider.ANTHROPIC;
            default -> hasOpenAiKey || !hasAnthropicKey ? Provider.OPENAI : Provider.ANTHROPIC;
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
