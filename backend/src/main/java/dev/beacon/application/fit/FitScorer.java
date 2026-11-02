package dev.beacon.application.fit;

import java.time.LocalDate;
import java.util.List;

/**
 * Scores how well a resume fits a job posting with one AI provider, using the key
 * of the user asking. Implementations throw {@link FitScoreException} on failure.
 */
public interface FitScorer {

    /** The provider's id in the {@link dev.beacon.application.providers.ProviderCatalog}, e.g. "openai". */
    String provider();

    /** The model it scores with, kept with each result. */
    String model();

    FitResult score(FitInput input, String apiKey);

    /**
     * @param company null when the employer isn't disclosed
     * @param today   so a deadline without a year ("apply by Nov 15") can be placed
     */
    record FitInput(String company, String role, String jobDescription, String resume, LocalDate today) {}

    /**
     * @param score        0 to 100
     * @param dealbreakers hard requirements that often rule a candidate out: clearance,
     *                     sponsorship, on-site location, a required license
     * @param applyBy      the application deadline when the posting states one; null otherwise
     */
    record FitResult(int score, List<String> matchedSkills, List<String> gaps, List<String> dealbreakers,
                     String rationale, LocalDate applyBy) {}
}
