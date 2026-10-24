package dev.beacon.application.coverletters;

/** Drafts cover letters. Implementations throw {@link CoverLetterException} on failure. */
public interface CoverLetterWriter {

    /** False until the writer's credentials are configured. */
    boolean isConfigured();

    String draft(CoverLetterInput input);

    /**
     * @param instructions optional guidance from the user, e.g. "mention my move to Denver"
     */
    record CoverLetterInput(String company, String role, String jobDescription, String resume, String instructions) {}
}
