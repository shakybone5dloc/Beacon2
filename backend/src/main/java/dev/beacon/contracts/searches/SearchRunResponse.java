package dev.beacon.contracts.searches;

import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * @param gathered what the sources returned, before filtering and dedupe
 * @param notes    a line per source ("Remotive: 12") and anything that went wrong
 */
public record SearchRunResponse(Instant startedAt, @Nullable Instant finishedAt, int gathered, int newMatches,
                                int scored, List<String> notes) {}
