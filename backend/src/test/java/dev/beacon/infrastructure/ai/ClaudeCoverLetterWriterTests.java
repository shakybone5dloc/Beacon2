package dev.beacon.infrastructure.ai;

import dev.beacon.application.coverletters.CoverLetterException;
import dev.beacon.application.coverletters.CoverLetterWriter.CoverLetterInput;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaudeCoverLetterWriterTests {

    private static final CoverLetterInput INPUT =
            new CoverLetterInput("Acme", "Java Engineer", "Build services.", "Jordan Smith\nJava, 8 years", "Mention Denver");

    @Test
    void isOffWithoutAKeyAndSaysHowToFixIt() {
        var writer = new ClaudeCoverLetterWriter("", "claude-opus-5-5");

        assertThat(writer.isConfigured()).isFalse();
        assertThatThrownBy(() -> writer.draft(INPUT))
                .isInstanceOf(CoverLetterException.class)
                .hasMessageContaining("ANTHROPIC_API_KEY");
    }

    @Test
    void promptWrapsTheDocumentsInTagsAndAddsGuidance() {
        var message = CoverLetterPrompt.userMessage(INPUT);

        assertThat(message).contains("Company: Acme").contains("Role: Java Engineer");
        assertThat(message).contains("<job_description>\nBuild services.\n</job_description>");
        assertThat(message).contains("<resume>\nJordan Smith\nJava, 8 years\n</resume>");
        assertThat(message).endsWith("Additional guidance from the candidate: Mention Denver");
    }

    @Test
    void promptOmitsBlankGuidance() {
        var message = CoverLetterPrompt.userMessage(new CoverLetterInput("Acme", "Engineer", "JD", "CV", "  "));

        assertThat(message).doesNotContain("Additional guidance");
    }
}
