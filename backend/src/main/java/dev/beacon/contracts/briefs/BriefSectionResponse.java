package dev.beacon.contracts.briefs;

import dev.beacon.domain.briefs.SectionKind;
import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * One section of a brief.
 *
 * @param interviewId set on a round's update: the interview it's about
 * @param writable    true when the AI can write it; the others are the user's to fill in
 * @param content     Markdown; null until written. The checklist starts from a template
 * @param edited      true when the user has changed it since the AI last wrote it
 * @param version     null until the section is saved; send it back with an edit
 */
public record BriefSectionResponse(SectionKind kind, @Nullable UUID interviewId, String title, boolean writable,
                                   @Nullable String content, @Nullable Instant generatedAt, @Nullable String model,
                                   boolean edited, @Nullable Long version) {}
