package dev.beacon.application.postings;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Local filtering for sources that return a whole feed or search loosely.
 * Terms match as whole words, so "java" does not match "javascript" but
 * "c++" and ".net" still work.
 */
public final class PostingMatcher {

    private static final List<String> ANYWHERE = List.of("worldwide", "anywhere", "global");

    private static final List<List<String>> LOCATION_ALIASES = List.of(
            List.of("usa", "us", "u.s.", "united states", "america", "americas", "north america"),
            List.of("uk", "united kingdom", "great britain", "england"),
            List.of("europe", "eu", "emea"));

    private PostingMatcher() {}

    public static boolean matches(JobPosting posting, PostingQuery query) {
        return matchesKeywords(posting, query.keywords())
                && matchesLocation(posting.location(), query.location())
                && (!query.remoteOnly() || posting.remote());
    }

    /** Every term must appear in the title, company, tags or snippet. */
    public static boolean matchesKeywords(JobPosting posting, String keywords) {
        return matchesKeywords(keywords, Stream.concat(
                Stream.of(posting.title(), posting.company(), posting.snippet()),
                posting.tags().stream()
        ).toList());
    }

    /** Every term must appear somewhere in the given texts; nulls are skipped. */
    public static boolean matchesKeywords(String keywords, List<String> texts) {
        if (keywords.isBlank()) return true;

        var haystack = String.join(" ", texts.stream().filter(s -> s != null).toList()).toLowerCase(Locale.ROOT);

        return Arrays.stream(keywords.toLowerCase(Locale.ROOT).split("\\s+"))
                .allMatch(term -> containsWord(haystack, term));
    }

    /** A posting with no location, or one open to anywhere, matches every location. */
    public static boolean matchesLocation(String postingLocation, String wanted) {
        if (wanted.isBlank() || postingLocation == null || postingLocation.isBlank()) return true;

        var where = postingLocation.toLowerCase(Locale.ROOT);
        var want = wanted.toLowerCase(Locale.ROOT);

        if (ANYWHERE.stream().anyMatch(where::contains)) return true;

        var aliases = LOCATION_ALIASES.stream()
                .filter(group -> group.contains(want))
                .findFirst()
                .orElse(List.of(want));
        return aliases.stream().anyMatch(alias -> containsWord(where, alias));
    }

    private static boolean containsWord(String haystack, String term) {
        var pattern = Pattern.compile("(?<![a-z0-9])" + Pattern.quote(term) + "(?![a-z0-9])");
        return pattern.matcher(haystack).find();
    }
}
