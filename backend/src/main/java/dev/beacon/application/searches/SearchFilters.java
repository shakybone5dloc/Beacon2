package dev.beacon.application.searches;

import dev.beacon.contracts.postings.JobPostingResponse;

import java.text.Normalizer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The filters a saved search applies without AI. A posting that doesn't say something
 * (its pay, its date) is kept: dropping it would hide jobs that may well fit.
 */
final class SearchFilters {

    private static final Pattern NOT_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    /** The marks NFD splits off accented letters: "ê" becomes "e" plus one of these. */
    private static final Pattern ACCENTS = Pattern.compile("\\p{M}+");

    private SearchFilters() {}

    static boolean keep(JobPostingResponse posting, List<String> excludeWords, Integer salaryMin,
                        int postedWithinDays, Instant now) {
        return !excluded(posting.title(), excludeWords)
                && paysEnough(posting, salaryMin)
                && recentEnough(posting.postedAt(), postedWithinDays, now);
    }

    /**
     * Whole words or phrases, ignoring case and accents: "Senior" drops "Senior Engineer"
     * and "Desenvolvedor Sênior", but not "Seniority Analyst".
     */
    static boolean excluded(String title, List<String> excludeWords) {
        var padded = " " + words(title) + " ";
        return excludeWords.stream()
                .map(SearchFilters::words)
                .filter(word -> !word.isEmpty())
                .anyMatch(word -> padded.contains(" " + word + " "));
    }

    /** Lower case without accents, with anything that isn't a letter or digit turned into single spaces. */
    private static String words(String text) {
        var plain = ACCENTS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
        return NOT_WORD.matcher(plain.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
    }

    /**
     * Compared against the top of the range, and only for yearly pay in the same terms,
     * since an hourly rate or a monthly figure can't be held to a yearly floor fairly.
     */
    static boolean paysEnough(JobPostingResponse posting, Integer salaryMin) {
        if (salaryMin == null) return true;
        var top = posting.salaryMax() != null ? posting.salaryMax() : posting.salaryMin();
        if (top == null) return true;
        var period = posting.salaryPeriod();
        if (period != null && !period.equalsIgnoreCase("year")) return true;
        return top >= salaryMin;
    }

    static boolean recentEnough(Instant postedAt, int postedWithinDays, Instant now) {
        return postedAt == null || !postedAt.isBefore(now.minus(postedWithinDays, ChronoUnit.DAYS));
    }
}
