package dev.beacon.infrastructure.postings;

import dev.beacon.application.postings.CareersBoardClient;
import dev.beacon.application.postings.JobPosting;
import dev.beacon.domain.companies.CareersPlatform;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Open roles from a company's Oracle Recruiting Cloud careers site
 * ({pod}.fa.{region}.oraclecloud.com, site CX_n), through the JSON the site itself loads.
 * Not a documented API, so it may change. Boards run to thousands of roles, so searches
 * run on Oracle, and only the top results get a second call for their full description.
 */
@Component
public class OracleBoardClient implements CareersBoardClient {

    /** Roles per list call; also how many search results get their full description. */
    static final int PAGE = 20;

    /** A date with no time is kept as noon UTC, so it shows as the same day in every US and European time zone. */
    private static final LocalTime NOON = LocalTime.NOON;

    private final RestClient http;
    private final TtlCache<String, List<JobPosting>> lists;     // one call per board and search per hour
    private final TtlCache<String, JobPosting> details;        // one call per role per hour

    public OracleBoardClient(RestClient.Builder builder, Clock clock) {
        this.http = builder.build();
        this.lists = new TtlCache<>(Duration.ofHours(1), clock, 200);
        this.details = new TtlCache<>(Duration.ofHours(1), clock, 2000);
    }

    @Override public CareersPlatform platform() { return CareersPlatform.ORACLE; }

    /** The newest 20 roles. */
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
        // The finder's parameters are separated by commas and semicolons, so those can't be in the keyword.
        var keyword = keywords.replaceAll("[,;=\"]", " ").replaceAll("\\s+", " ").strip();
        var finder = "findReqs;siteNumber=" + board.siteNumber() + ",limit=" + PAGE + ",sortBy=POSTING_DATES_DESC"
                + (keyword.isEmpty() ? "" : ",keyword=" + encode(keyword));
        var body = http.get()
                .uri(URI.create(board.host() + "/hcmRestApi/resources/latest/recruitingCEJobRequisitions"
                        + "?onlyData=true&expand=requisitionList&finder=" + finder))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode.class);
        var postings = new ArrayList<JobPosting>();
        for (var job : body.path("items").path(0).path("requisitionList").values()) {
            var id = Feeds.text(job, "Id");
            var title = Feeds.text(job, "Title");
            if (id == null || title == null) continue;
            var location = Feeds.text(job, "PrimaryLocation");
            postings.add(new JobPosting(
                    "watchlist", "oracle:" + id, title, companyName, location,
                    isRemote(location, Feeds.text(job, "WorkplaceType")),
                    board.jobUrl(id), date(Feeds.text(job, "PostedDate")), localDate(Feeds.text(job, "PostingEndDate")),
                    null, null, null, null, null, List.of(), Feeds.text(job, "ShortDescriptionStr"), null));
        }
        return postings;
    }

    /** The full role: description, responsibilities and qualifications. */
    private JobPosting detail(Board board, JobPosting listed) {
        var id = listed.id().substring("oracle:".length());
        var finder = "ById;Id=" + encode("\"" + id + "\"") + ",siteNumber=" + board.siteNumber();
        var job = http.get()
                .uri(URI.create(board.host() + "/hcmRestApi/resources/latest/recruitingCEJobRequisitionDetails"
                        + "?expand=all&onlyData=true&finder=" + finder))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode.class)
                .path("items").path(0);
        var html = Stream.of("ExternalDescriptionStr", "ExternalResponsibilitiesStr", "ExternalQualificationsStr")
                .map(field -> Feeds.text(job, field))
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"));
        if (html.isEmpty()) return listed;
        var closes = localDate(Feeds.text(job, "ExternalPostedEndDate"));
        return new JobPosting(
                listed.source(), listed.id(), listed.title(), listed.company(), listed.location(),
                listed.remote() || isRemote(null, Feeds.text(job, "WorkplaceType")), listed.url(), listed.postedAt(),
                closes != null ? closes : listed.closesOn(),
                null, null, null, null, null, listed.tags(), Feeds.snippet(html), Feeds.description(html));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static Instant date(String value) {
        var day = localDate(value);
        return day == null ? null : day.atTime(NOON).toInstant(ZoneOffset.UTC);
    }

    /** "2026-11-01", or the date part of a timestamp. */
    private static LocalDate localDate(String value) {
        try {
            return value == null ? null : LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static boolean isRemote(String location, String workplaceType) {
        return Stream.of(location, workplaceType)
                .anyMatch(text -> text != null && text.toLowerCase(Locale.ROOT).contains("remote"));
    }

    /** "eeho.fa.us2/CX_45001": the careers site's host and site number. */
    private record Board(String host, String siteNumber) {
        static Board parse(String boardId) {
            var slash = boardId.indexOf('/');
            return new Board("https://" + boardId.substring(0, slash) + ".oraclecloud.com", boardId.substring(slash + 1));
        }

        String jobUrl(String id) {
            return host + "/hcmUI/CandidateExperience/en/sites/" + siteNumber + "/job/" + id;
        }
    }
}
