package dev.beacon.infrastructure.ai;

import dev.beacon.application.stories.StoryException;
import dev.beacon.application.stories.StoryWriter.DraftInput;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoryPromptTests {

    @Test
    void asksForOneStoryFromNotesOrSuggestionsFromTheResume() {
        assertThat(StoryPrompt.userMessage(new DraftInput("Made checkout fast.", "Jordan Smith")))
                .isEqualTo("<notes>\nMade checkout fast.\n</notes>\n\n<resume>\nJordan Smith\n</resume>");
        assertThat(StoryPrompt.userMessage(new DraftInput(null, "Jordan Smith")))
                .startsWith("No notes: suggest stories from the resume.");
        assertThat(StoryPrompt.INSTRUCTIONS).contains("up to 5 distinct stories", "40% faster checkout");
    }

    @Test
    void keepsStoriesWithATitleAndSomethingToTell() {
        var drafts = StoryPrompt.parse("""
                {"stories": [
                  {"title": " Cut checkout latency ", "situation": "Slow.", "task": "", "action": "Cached.",
                   "result": "Fast.", "metrics": "", "tags": ["performance", " ", "hardest problem"]},
                  {"title": "", "situation": "No title.", "task": "", "action": "", "result": "", "metrics": "",
                   "tags": []},
                  {"title": "Empty", "situation": " ", "task": "", "action": "", "result": "", "metrics": "40%",
                   "tags": []}
                ]}
                """);

        assertThat(drafts).singleElement().satisfies(d -> {
            assertThat(d.title()).isEqualTo("Cut checkout latency");
            assertThat(d.tags()).containsExactly("performance", "hardest problem");
        });
    }

    @Test
    void anUnreadableOrEmptyReplyIsAnError() {
        assertThatThrownBy(() -> StoryPrompt.parse("not json")).isInstanceOf(StoryException.class)
                .hasMessageContaining("couldn't read");
        assertThatThrownBy(() -> StoryPrompt.parse("{\"stories\": []}")).isInstanceOf(StoryException.class)
                .hasMessageContaining("didn't draft any");
    }
}
