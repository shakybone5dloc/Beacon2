package dev.beacon.contracts.stories;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A new story, or the whole of an existing one.
 *
 * @param version the story's version as loaded; leave out for a new one
 */
public record SaveStoryRequest(
        @NotBlank @Size(max = 200) String title,
        @Nullable @Size(max = 5000) String situation,
        @Nullable @Size(max = 5000) String task,
        @Nullable @Size(max = 10000) String action,
        @Nullable @Size(max = 5000) String result,
        @Nullable @Size(max = 2000) String metrics,
        @Nullable @Size(max = 12) List<@Size(max = 40) String> tags,
        @Nullable Long version) {}
