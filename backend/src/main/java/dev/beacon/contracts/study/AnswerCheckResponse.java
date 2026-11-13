package dev.beacon.contracts.study;

import dev.beacon.domain.study.Grade;

/**
 * The AI's read of a typed or spoken answer; nothing is saved until the user grades.
 *
 * @param suggestedGrade the grade it suggests; the user confirms or picks another
 * @param feedback       what was right and what was missed, briefly (Markdown)
 * @param tighter        a crisper way to say it, in the user's own voice
 */
public record AnswerCheckResponse(Grade suggestedGrade, String feedback, String tighter) {}
