package dev.beacon.domain.briefs;

/**
 * The parts of an interview brief, in the order they're read. Some the AI writes from the
 * posting, resume and fit; the rest start as a template or blank for the user to fill in.
 */
public enum SectionKind {
    PITCH("The role and your pitch", true),
    CHECKLIST("Day-of checklist", false),
    /** Researched on the web, every fact with its source. */
    COMPANY("About the company and team", true),
    GAP("Your honest gap, and how to frame it", true),
    STORIES("Stories to tell", true),
    QUESTIONS("Likely questions", true),
    REFRESHER("Technical refresher", true),
    PRACTICE("System design practice", true),
    /** One per interview round, written from that round's debrief notes. */
    ROUND("Update after the round", true),
    QUESTIONS_TO_ASK("Questions to ask, and closing", true);

    private final String title;
    private final boolean written;

    SectionKind(String title, boolean written) {
        this.title = title;
        this.written = written;
    }

    public String title() { return title; }

    /** True when the AI can write it; the others are the user's to fill in. */
    public boolean isWritten() { return written; }

    public boolean isPerRound() { return this == ROUND; }
}
