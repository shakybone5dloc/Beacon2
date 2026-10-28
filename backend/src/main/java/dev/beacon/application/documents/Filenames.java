package dev.beacon.application.documents;

import java.util.Locale;

/** File names for the PDFs Beacon saves. */
public final class Filenames {

    private Filenames() { }

    /** Lower-case words joined by dashes, at most 60 characters: "Acme, Inc." becomes "acme-inc". */
    public static String slug(String text) {
        var slug = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (slug.isEmpty()) return "job";
        return slug.length() <= 60 ? slug : slug.substring(0, 60);
    }
}
