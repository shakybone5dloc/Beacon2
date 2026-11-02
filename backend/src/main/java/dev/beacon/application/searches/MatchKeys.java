package dev.beacon.application.searches;

import dev.beacon.domain.companies.CompanyNames;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * One key per job, from its company and title, so the same job found by two sources, or
 * again tomorrow, or already on the board, is recognised. "Acme, Inc." and "ACME" match,
 * as do "Sr. Java Engineer (Remote)" and "sr java engineer remote".
 */
final class MatchKeys {

    private static final Pattern NOT_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

    private MatchKeys() {}

    static String of(String company, String title) {
        var plain = normalizeCompany(company) + "|" + normalizeTitle(title);
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(plain.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is always available", ex);
        }
    }

    private static String normalizeCompany(String company) {
        return company == null || company.isBlank() ? "" : CompanyNames.normalize(company);
    }

    private static String normalizeTitle(String title) {
        return NOT_WORD.matcher(title == null ? "" : title.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
    }
}
