package dev.beacon.application.documents;

import java.util.List;
import java.util.Locale;

/**
 * Recognizes the "checking your browser" and "you have been blocked" pages bot
 * protection shows a headless browser instead of the posting. Beacon reports these
 * rather than saving them; it never tries to get past them.
 */
final class BotChecks {

    /** Challenge pages are short; a real posting that mentions one of these phrases is not. */
    private static final int MAX_CHALLENGE_LENGTH = 3_000;

    private static final List<String> MARKERS = List.of(
            "performing security verification",
            "verify you are human",
            "verify you are a human",
            "checking your browser",
            "checking if the site connection is secure",
            "enable javascript and cookies to continue",
            "performance and security by cloudflare",
            "attention required! | cloudflare",
            "pardon our interruption",
            "are you a robot",
            "request unsuccessful. incapsula",
            // Blocked outright rather than challenged, e.g. Indeed behind Cloudflare.
            "request blocked",
            "you have been blocked",
            "troubleshooting cloudflare errors",
            "cloudflare ray id",
            "your ray id for this request",
            "access denied",
            "unusual traffic from your computer",
            "press & hold",
            "press and hold");

    private BotChecks() {}

    static boolean looksLikeBotCheck(String pageText) {
        if (pageText == null || pageText.length() > MAX_CHALLENGE_LENGTH) return false;
        var text = pageText.toLowerCase(Locale.ROOT);
        return MARKERS.stream().anyMatch(text::contains);
    }
}
