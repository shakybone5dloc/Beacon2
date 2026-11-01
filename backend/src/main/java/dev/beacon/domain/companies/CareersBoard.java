package dev.beacon.domain.companies;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Where a company posts its jobs: the host and the company's board id there. */
public record CareersBoard(CareersPlatform platform, String boardId) {

    // boards.greenhouse.io/acme, job-boards.greenhouse.io/acme, jobs.lever.co/acme, jobs.ashbyhq.com/acme
    private static final Pattern GREENHOUSE = Pattern.compile("^https?://(?:job-)?boards\\.greenhouse\\.io/([A-Za-z0-9_-]+)");
    private static final Pattern LEVER = Pattern.compile("^https?://jobs\\.lever\\.co/([A-Za-z0-9_.-]+)");
    private static final Pattern ASHBY = Pattern.compile("^https?://jobs\\.ashbyhq\\.com/([A-Za-z0-9_.%-]+)");

    /** Empty when the link isn't a board Beacon knows. */
    public static Optional<CareersBoard> fromUrl(String url) {
        if (url == null) return Optional.empty();
        var trimmed = url.strip();
        for (var entry : Map.of(CareersPlatform.GREENHOUSE, GREENHOUSE, CareersPlatform.LEVER, LEVER,
                CareersPlatform.ASHBY, ASHBY).entrySet()) {
            var m = entry.getValue().matcher(trimmed);
            if (m.find()) return Optional.of(new CareersBoard(entry.getKey(), m.group(1)));
        }
        return Optional.empty();
    }
}