package dev.beacon.contracts.stories;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A story from the bank.
 *
 * @param gaps placeholders still to fill in, like "how many users?", from "[how many users?]" in the text
 */
public record StoryResponse(UUID id, String title, String situation, String task, String action, String result,
                            String metrics, List<String> tags, List<String> gaps, long version, Instant createdAt,
                            Instant updatedAt) {}
