package dev.beacon.contracts.study;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** @param storyIds the stories to drill, e.g. the ones a tag filter shows; null or empty for every story */
public record DrillStoriesRequest(@Nullable @Size(max = 500) List<UUID> storyIds) {}
