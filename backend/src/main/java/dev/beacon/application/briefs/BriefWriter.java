package dev.beacon.application.briefs;

import dev.beacon.application.usage.AiUsageMeter;
import dev.beacon.domain.briefs.SectionKind;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Writes one section of an interview brief with one AI provider, using the key of the
 * user asking. Implementations throw {@link BriefException} on failure.
 */
public interface BriefWriter {

    /** The provider's id in the {@link dev.beacon.application.providers.ProviderCatalog}, e.g. "openai". */
    String provider();

    /** The model it writes with, kept with each section. */
    String model();

    /** Markdown for the section. Reports the tokens used to {@code meter} as soon as the response arrives. */
    String write(BriefInput input, String apiKey, AiUsageMeter meter);

    /**
     * Everything Beacon knows about the application, for any section.
     *
     * @param company      null when the employer isn't disclosed
     * @param fit          the latest fit check, when there is one
     * @param roundId      for {@link SectionKind#ROUND}: the interview the update is about
     */
    record BriefInput(SectionKind section, UUID roundId, String company, String role, String location,
                      Boolean remote, String employmentType, String pay, String jobDescription, String notes,
                      String resume, Fit fit, List<PersonLine> people, List<Round> rounds, LocalDate today) {}

    record Fit(int score, List<String> matchedSkills, List<String> gaps, List<String> dealbreakers, String rationale) {}

    /** Someone on the application: recruiter, hiring manager, interviewer... */
    record PersonLine(String name, String role, String title, String company) {}

    /** One interview round, in order. {@code when} is already formatted in the round's own time zone. */
    record Round(UUID id, String type, String when, List<String> interviewers, String prepNotes,
                 String debriefNotes, String outcome) {}
}
