package dev.beacon.domain.companies;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where a company posts its jobs: the host and the company's board id there.
 * Workday ids are "tenant.wdN/site" and Oracle ids "pod.fa.region/siteNumber",
 * since those hosts differ per company.
 */
public record CareersBoard(CareersPlatform platform, String boardId) {

    // boards.greenhouse.io/embed/job_board?for=acme, the embed many companies put on their own site
    private static final Pattern GREENHOUSE_EMBED = Pattern.compile("^https?://(?:job-)?boards\\.greenhouse\\.io/embed/job_board(?:/js)?\\?for=([A-Za-z0-9_-]+)");
    // boards.greenhouse.io/acme, job-boards.greenhouse.io/acme, jobs.lever.co/acme, jobs.ashbyhq.com/acme
    private static final Pattern GREENHOUSE = Pattern.compile("^https?://(?:job-)?boards\\.greenhouse\\.io/([A-Za-z0-9_-]+)");
    private static final Pattern LEVER = Pattern.compile("^https?://jobs\\.lever\\.co/([A-Za-z0-9_.-]+)");
    private static final Pattern ASHBY = Pattern.compile("^https?://jobs\\.ashbyhq\\.com/([A-Za-z0-9_.%-]+)");
    // elevancehealth.wd1.myworkdayjobs.com/ANT, also with a locale: .../en-US/ANT/job/...
    private static final Pattern WORKDAY = Pattern.compile(
            "^https?://([A-Za-z0-9-]+)\\.(wd\\d+)\\.myworkdayjobs\\.com/(?:[a-z]{2}-[A-Z]{2}/)?([A-Za-z0-9_-]+)");
    // eeho.fa.us2.oraclecloud.com/hcmUI/CandidateExperience/en/sites/CX_45001/...
    private static final Pattern ORACLE = Pattern.compile(
            "^https?://([A-Za-z0-9-]+\\.fa(?:\\.[A-Za-z0-9-]+)*)\\.oraclecloud\\.com/hcmUI/CandidateExperience/[A-Za-z-]+/sites/(CX_\\d+)");

    private static final Map<Pattern, Function<Matcher, CareersBoard>> PARSERS = parsers();

    /** Empty when the link isn't a board Beacon knows. */
    public static Optional<CareersBoard> fromUrl(String url) {
        if (url == null) return Optional.empty();
        var trimmed = url.strip();
        for (var parser : PARSERS.entrySet()) {
            var m = parser.getKey().matcher(trimmed);
            if (m.find()) return Optional.of(parser.getValue().apply(m));
        }
        return Optional.empty();
    }

    /** An Oracle Recruiting Cloud board, from its host ("eeho.fa.us2") and site number ("CX_45001"). */
    public static CareersBoard oracle(String host, String siteNumber) {
        return new CareersBoard(CareersPlatform.ORACLE, host.toLowerCase(Locale.ROOT) + "/" + siteNumber);
    }

    /** Checked in order: the Greenhouse embed before the plain Greenhouse link it also looks like. */
    private static Map<Pattern, Function<Matcher, CareersBoard>> parsers() {
        var parsers = new LinkedHashMap<Pattern, Function<Matcher, CareersBoard>>();
        parsers.put(GREENHOUSE_EMBED, m -> new CareersBoard(CareersPlatform.GREENHOUSE, m.group(1)));
        parsers.put(GREENHOUSE, m -> new CareersBoard(CareersPlatform.GREENHOUSE, m.group(1)));
        parsers.put(LEVER, m -> new CareersBoard(CareersPlatform.LEVER, m.group(1)));
        parsers.put(ASHBY, m -> new CareersBoard(CareersPlatform.ASHBY, m.group(1)));
        parsers.put(WORKDAY, m -> new CareersBoard(CareersPlatform.WORKDAY,
                m.group(1).toLowerCase(Locale.ROOT) + "." + m.group(2) + "/" + m.group(3)));
        parsers.put(ORACLE, m -> oracle(m.group(1), m.group(2)));
        return parsers;
    }
}
