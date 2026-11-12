package dev.beacon.domain.study;

import dev.beacon.domain.stories.Story;

/**
 * How a story from the bank (A7) reads as a study card: its title to prompt, its STAR outline to
 * check against. Shared by every place that makes or refreshes one, so they always match.
 */
public final class StoryCard {

    private StoryCard() {}

    public static String question(Story story) {
        var tags = story.getTags().isEmpty() ? "" : " (" + String.join(", ", story.getTags()) + ")";
        return "Tell the story: " + story.getTitle() + tags;
    }

    public static String answer(Story story) {
        var answer = new StringBuilder();
        line(answer, "Situation", story.getSituation());
        line(answer, "Task", story.getTask());
        line(answer, "Action", story.getAction());
        line(answer, "Result", story.getResult());
        line(answer, "Numbers", story.getMetrics());
        return answer.toString().strip();
    }

    private static void line(StringBuilder text, String label, String value) {
        if (value != null && !value.isBlank()) {
            text.append("**").append(label).append(":** ").append(value.strip()).append("\n\n");
        }
    }
}
