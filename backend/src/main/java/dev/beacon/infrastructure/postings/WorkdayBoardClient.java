package dev.beacon.infrastructure.postings;

import dev.beacon.application.postings.CareersBoardClient;
import dev.beacon.application.postings.JobPosting;
import dev.beacon.domain.companies.CareersPlatform;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Open roles from a company's Workday careers site ({tenant}.wdN.myworkdayjobs.com/{site}),
 * through the JSON the site itself loads. Not a documented API, so it may change. Large
 * employers list thousands of roles 20 at a time, so searches run on Workday, and only
 * the top results get a second call for their full description.
 */
@Component
public class WorkdayBoardClient implements CareersBoardClient {

    /** Workday's page size; also how many search results get their full description. */
    static final int PAGE = 20;

    /** A date with no time is kept as noon UTC, so it shows as the same day in every US and European time zone. */
    private static final LocalTime NOON = LocalTime.NOON;

    private static final Pattern DAYS_AGO = Pattern.compile("Posted (\\d+) Days? Ago");

    private final RestClient http;
    private final Clock clock;
    private final TtlCache<String, List<JobPosting>> lists;     // one call per board and search per hour
    private final TtlCache<String, JobPosting> details;        // one call per role per hour

    public WorkdayBoardClient(RestClient.Builder builder, Clock clock) {
        this.http = builder.build();
        this.clock = clock;
        this.lists = new TtlCache<>(Duration.ofHours(1), clock, 200);
        this.details = new TtlCache<>(Duration.ofHours(1), clock, 2000);
    }

    @Override public CareersPlatform platform() { return CareersPlatform.WORKDAY; }

    /** The newest 20 roles; Workday lists newest first. */
    @Override
    public List<JobPosting> openings(String boardId, String companyName) {
        return lists.get(boardId + "|" + companyName + "|", () -> list(Board.parse(boardId), companyName, ""));
    }

    @Override
    public List<JobPosting> search(String boardId, String companyName, String keywords) {
        var board = Board.parse(boardId);
        var found = lists.get(boardId + "|" + companyName + "|" + keywords, () -> list(board, companyName, keywords));
        return Details.fill(found, posting -> details.getConcurrently(posting.url(), () -> detail(board, posting)));
    }

    private List<JobPosting> list(Board board, String companyName, String keywords) {
        var body = http.post()
                .uri(board.api() + "/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(Map.of("appliedFacets", Map.of(), "limit", PAGE, "offset", 0, "searchText", keywords))
                .retrieve()
                .body(JsonNode.class);
        var postings = new ArrayList<JobPosting>();
        for (var job : body.path("jobPostings").values()) {
            var path = Feeds.text(job, "externalPath");
            var title = Feeds.text(job, "title");
            if (path == null || title == null) continue;
            var location = Feeds.text(job, "locationsText");
            postings.add(new JobPosting(
                    "watchlist", "workday:" + path, title, companyName, location, isRemote(location),
                    board.site() + path, postedAt(Feeds.text(job, "postedOn")),
                    null, null, null, null, null, null, Feeds.strings(job, "bulletFields"), null, null));
        }
        return postings;
    }

    /** The full role: description, every location, posted and closing dates. */
    private JobPosting detail(Board board, JobPosting listed) {
        var path = listed.id().substring("workday:".length());
        var info = http.get().uri(board.api() + path).accept(MediaType.APPLICATION_JSON)
                .retrieve().body(JsonNode.class).path("jobPostingInfo");
        var location = Feeds.text(info, "location");
        var more = info.path("additionalLocations").size();
        if (location != null && more > 0) location += " + " + more + " more";
        var html = Feeds.text(info, "jobDescription");
        var url = Feeds.text(info, "externalUrl");
        return new JobPosting(
                listed.source(), listed.id(), listed.title(), listed.company(),
                location != null ? location : listed.location(),
                isRemote(location) || "Remote".equalsIgnoreCase(Feeds.text(info, "remoteType")),
                url != null ? url : listed.url(),
                date(Feeds.text(info, "startDate"), listed.postedAt()),
                localDate(Feeds.text(info, "endDate")),
                null, null, null, null, null, listed.tags(),
                Feeds.snippet(html), Feeds.description(html));
    }

    /** "Posted Today", "Posted Yesterday", "Posted 3 Days Ago"; "30+ Days Ago" is too vague to use. */
    private Instant postedAt(String postedOn) {
        if (postedOn == null || postedOn.contains("+")) return null;
        var today = LocalDate.now(clock);
        if (postedOn.equals("Posted Today")) return today.atTime(NOON).toInstant(ZoneOffset.UTC);
        if (postedOn.equals("Posted Yesterday")) return today.minusDays(1).atTime(NOON).toInstant(ZoneOffset.UTC);
        var m = DAYS_AGO.matcher(postedOn);
        return m.matches() ? today.minusDays(Long.parseLong(m.group(1))).atTime(NOON).toInstant(ZoneOffset.UTC) : null;
    }

    private static Instant date(String value, Instant fallback) {
        var day = localDate(value);
        return day == null ? fallback : day.atTime(NOON).toInstant(ZoneOffset.UTC);
    }

    private static LocalDate localDate(String value) {
        try {
            return value == null ? null : LocalDate.parse(value);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static boolean isRemote(String location) {
        return location != null && location.toLowerCase(Locale.ROOT).contains("remote");
    }

    /** "elevancehealth.wd1/ANT": the careers site's host and site name. */
    private record Board(String tenant, String host, String siteName) {
        static Board parse(String boardId) {
            var slash = boardId.indexOf('/');
            var hostPart = boardId.substring(0, slash);
            return new Board(hostPart.substring(0, hostPart.indexOf('.')), "https://" + hostPart + ".myworkdayjobs.com",
                    boardId.substring(slash + 1));
        }

        String api() { return host + "/wday/cxs/" + tenant + "/" + siteName; }

        String site() { return host + "/" + siteName; }
    }
}
