package dev.beacon.domain.applications;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Job titles compare loosely: "Sr. Java Engineer" and "senior java engineer" are the
 * same role. Used to spot two recruiters pitching the same job.
 */
public final class JobTitles {

    private static final Pattern NOT_WORD = Pattern.compile("[^\\p{L}\\p{N}+#]+");
    private static final Pattern SENIOR = Pattern.compile("\\bsr\\b");
    private static final Pattern JUNIOR = Pattern.compile("\\bjr\\b");

    private JobTitles() { }

    public static String normalize(String title) {
        var words = NOT_WORD.matcher(title.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
        return JUNIOR.matcher(SENIOR.matcher(words).replaceAll("senior")).replaceAll("junior");
    }
}
