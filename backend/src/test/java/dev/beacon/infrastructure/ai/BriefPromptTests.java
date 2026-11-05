package dev.beacon.infrastructure.ai;

import dev.beacon.application.briefs.BriefWriter.BriefInput;
import dev.beacon.application.briefs.BriefWriter.Fit;
import dev.beacon.application.briefs.BriefWriter.PersonLine;
import dev.beacon.application.briefs.BriefWriter.Round;
import dev.beacon.application.briefs.BriefWriter.StoryLine;
import dev.beacon.domain.briefs.SectionKind;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BriefPromptTests {

    private static final UUID SCREEN = UUID.randomUUID();
    private static final UUID PANEL = UUID.randomUUID();

    private static BriefInput input(SectionKind section, UUID roundId, Fit fit) {
        return new BriefInput(section, roundId, null, "Senior Java Engineer", "Denver, CO", true, null,
                "120,000-150,000 USD a year", "Build services with Java.", null, "Jordan Smith\nJava, Kafka",
                fit, List.of(new PersonLine("Sam Ortiz", "Interviewer", "Staff Engineer", "GHX")),
                List.of(new Round(SCREEN, "Phone screen", "Thu Oct 1, 2026 3:00 PM (UTC)", List.of("Sam Ortiz"),
                                null, "Asked about Kafka.", "Passed"),
                        new Round(PANEL, "Panel", "Thu Oct 8, 2026 3:00 PM (UTC)", List.of(), "Review design.",
                                null, null)),
                List.of(new StoryLine("Multi-tenant authorization at PropKnot", List.of("auth you've built"),
                        "Landlords shared one app.", "Keep tenants apart.", "Built row-level policies.",
                        "No cross-tenant leaks.", "[how many tenants?]")),
                LocalDate.parse("2026-11-04"));
    }

    @Test
    void onlyTheSectionsThatUseStoriesGetTheStoryBank() {
        var stories = BriefPrompt.userMessage(input(SectionKind.STORIES, null, null));

        assertThat(stories).contains("=== STORY BANK (their own stories) ===\n"
                + "### Multi-tenant authorization at PropKnot  (auth you've built)\n"
                + "Situation: Landlords shared one app.\n", "Numbers: [how many tenants?]");
        assertThat(BriefPrompt.userMessage(input(SectionKind.QUESTIONS, null, null))).contains("STORY BANK");
        assertThat(BriefPrompt.userMessage(input(SectionKind.REFRESHER, null, null))).doesNotContain("STORY BANK");
    }

    @Test
    void everySectionTheAiWritesHasATask() {
        var written = Arrays.stream(SectionKind.values()).filter(SectionKind::isWritten).toList();

        assertThat(BriefPrompt.TASKS.keySet()).containsExactlyInAnyOrderElementsOf(written);
    }

    @Test
    void theMessageCarriesTheTaskAndEverythingKnownLeavingOutWhatIsnt() {
        var message = BriefPrompt.userMessage(input(SectionKind.QUESTIONS, null, null));

        assertThat(message).startsWith("SECTION TO WRITE: Likely questions");
        assertThat(message).contains("Company: Not disclosed", "Remote: Yes", "Pay: 120,000-150,000 USD a year",
                "- Sam Ortiz (Interviewer, Staff Engineer, GHX)", "Round 1: Phone screen, Thu Oct 1, 2026",
                "  With: Sam Ortiz", "  Debrief notes:\n    Asked about Kafka.", "Round 2: Panel",
                "=== RESUME ===\nJordan Smith\nJava, Kafka");
        assertThat(message).doesNotContain("FIT CHECK", "Employment type", "TARGET ROUND", "Their notes");
    }

    @Test
    void aRoundUpdateMarksItsRoundAndTheFitIsIncludedWhenThereIsOne() {
        var fit = new Fit(78, List.of("Java"), List.of("Kubernetes"), List.of(), "Strong Java; no Kubernetes.");

        var message = BriefPrompt.userMessage(input(SectionKind.ROUND, SCREEN, fit));

        assertThat(message).contains("Round 1: Phone screen, Thu Oct 1, 2026 3:00 PM (UTC)  <-- TARGET ROUND");
        assertThat(message).doesNotContain("Panel, Thu Oct 8, 2026 3:00 PM (UTC)  <--");
        assertThat(message).contains("Score: 78 / 100", "Gaps: Kubernetes");
    }
}
