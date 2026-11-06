package dev.beacon.contracts.study;

import dev.beacon.domain.study.Grade;
import jakarta.validation.constraints.NotNull;

public record ReviewCardRequest(@NotNull Grade grade) {}
