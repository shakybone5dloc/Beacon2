package dev.beacon.contracts.study;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param answer what the user typed or said for the card */
public record CheckAnswerRequest(@NotBlank @Size(max = 10000) String answer) {}
