package dev.beacon.domain.companies;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Company names compare loosely: "Acme, Inc." and "ACME" are the same company.
 * V7__add_companies.sql does the same steps in SQL for the backfill; keep them in step.
 */
public final class CompanyNames {

    private static final Pattern PUNCTUATION = Pattern.compile("[.,]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    /** A legal-form word at the end, after a space; a name that is only "Co" stays as it is. */
    private static final Pattern LEGAL_SUFFIX =
            Pattern.compile(" (inc|incorporated|llc|ltd|limited|corp|corporation|co|plc|gmbh)$");

    private CompanyNames() { }

    public static String normalize(String name) {
        var lower = name.strip().toLowerCase(Locale.ROOT);
        var plain = WHITESPACE.matcher(PUNCTUATION.matcher(lower).replaceAll("")).replaceAll(" ");
        return LEGAL_SUFFIX.matcher(plain).replaceAll("").strip();
    }
}
