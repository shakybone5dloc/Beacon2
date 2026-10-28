package dev.beacon.infrastructure.ai;

import dev.beacon.application.coverletters.CoverLetterWriter.CoverLetterInput;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeCoverLetterWriterTests {

    private static final CoverLetterInput INPUT =
            new CoverLetterInput("Acme", "Java Engineer", "Build services.", "Jordan Smith\nJava, 8 years", "Mention Denver");

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

    @Test
    void promptSaysWhenTheEmployerIsNotDisclosed() {
        var message = CoverLetterPrompt.userMessage(new CoverLetterInput(null, "Engineer", "JD", "CV", null));

        assertThat(message).contains("Company: not disclosed").doesNotContain("null");
    }
}
