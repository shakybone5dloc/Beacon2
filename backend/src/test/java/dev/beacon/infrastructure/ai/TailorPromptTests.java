package dev.beacon.infrastructure.ai;

import dev.beacon.application.tailoring.ResumeTailor.TailorInput;
import dev.beacon.application.tailoring.TailoringException;
import dev.beacon.domain.tailoring.ResumeSuggestion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TailorPromptTests {

    @Test
    void passesThePostingTheResumeAndWhatTheFitFound() {
        var message = TailorPrompt.userMessage(new TailorInput("GHX", "Backend Engineer", " Java, Kubernetes ",
                "Jordan Smith", List.of("Kubernetes"), List.of("Java")));

        assertThat(message).startsWith("Role: Backend Engineer\nCompany: GHX\n\n<posting>\nJava, Kubernetes\n</posting>")
                .contains("<resume>\nJordan Smith\n</resume>", "Shown: Java\n", "Missing: Kubernetes\n");
        assertThat(TailorPrompt.userMessage(new TailorInput(null, "Dev", "Java", "Jordan", List.of(), List.of())))
                .doesNotContain("Company:", "fit check");
        assertThat(TailorPrompt.INSTRUCTIONS).contains("up to 8 specific edits", "Never invent experience");
    }

    @Test
    void keepsSuggestionsWithAChangeToMake() {
        var suggestions = TailorPrompt.parse("""
                {"suggestions": [
                  {"kind": "REWORD", "section": " Experience: Acme ", "suggestion": " Say Spring Boot. ",
                   "reason": "Named in the posting.", "example": "Built APIs in Spring Boot."},
                  {"kind": "SHUFFLE", "section": "", "suggestion": "Lead with the summary.", "reason": "", "example": " "},
                  {"kind": "CUT", "section": "Hobbies", "suggestion": "", "reason": "", "example": ""}
                ]}
                """);

        assertThat(suggestions).hasSize(2);
        assertThat(suggestions.get(0).section()).isEqualTo("Experience: Acme");
        assertThat(suggestions.get(0).suggestion()).isEqualTo("Say Spring Boot.");
        // An unknown kind falls back, a missing section gets a general one, and a blank example is none.
        assertThat(suggestions.get(1).kind()).isEqualTo(ResumeSuggestion.Kind.EMPHASIZE);
        assertThat(suggestions.get(1).section()).isEqualTo("Resume");
        assertThat(suggestions.get(1).example()).isNull();
    }

    @Test
    void anUnreadableOrEmptyReplyIsTheAisFault() {
        assertThatThrownBy(() -> TailorPrompt.parse("not json"))
                .isInstanceOf(TailoringException.class).extracting("upstream").isEqualTo(true);
        assertThatThrownBy(() -> TailorPrompt.parse("{\"suggestions\": []}"))
                .isInstanceOf(TailoringException.class).hasMessageContaining("didn't suggest");
    }
}
