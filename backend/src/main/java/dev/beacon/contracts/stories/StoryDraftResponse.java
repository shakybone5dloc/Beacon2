package dev.beacon.contracts.stories;

import java.util.List;

/** A story the AI drafted, not saved yet. {@code gaps} are its placeholders still to fill in. */
public record StoryDraftResponse(String title, String situation, String task, String action, String result,
                                 String metrics, List<String> tags, List<String> gaps) {}
