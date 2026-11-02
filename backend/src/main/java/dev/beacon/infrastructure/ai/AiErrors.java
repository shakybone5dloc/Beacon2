package dev.beacon.infrastructure.ai;

/**
 * Makes the exception a feature reports AI failures with, such as
 * {@code CoverLetterException::new}. {@code upstream} is true when the AI service
 * failed (502) and false when the request can't work as it stands (422).
 */
@FunctionalInterface
interface AiErrors {
    RuntimeException create(String message, boolean upstream);
}
