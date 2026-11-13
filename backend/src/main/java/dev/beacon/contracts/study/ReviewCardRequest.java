package dev.beacon.contracts.study;

import dev.beacon.domain.study.AnswerMode;
import dev.beacon.domain.study.Grade;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param grade          the grade the user chose, which schedules the card
 * @param mode           how it was answered; Reveal when left out
 * @param answer         what the user typed or said, in Type and Say mode
 * @param suggestedGrade the AI's suggestion, when the answer was checked
 * @param feedback       the AI's note on the answer, when it was checked
 */
public record ReviewCardRequest(@NotNull Grade grade,
                                @Nullable AnswerMode mode,
                                @Nullable @Size(max = 10000) String answer,
                                @Nullable Grade suggestedGrade,
                                @Nullable @Size(max = 4000) String feedback) {}
