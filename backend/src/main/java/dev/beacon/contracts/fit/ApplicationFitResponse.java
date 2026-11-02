package dev.beacon.contracts.fit;

import jakarta.annotation.Nullable;

/** An application's fit; null until it's been checked. */
public record ApplicationFitResponse(@Nullable FitResponse fit) {
}
