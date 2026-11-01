package dev.beacon.infrastructure.postings;

import dev.beacon.application.postings.CareersBoardClient;
import dev.beacon.application.postings.JobPosting;
import dev.beacon.domain.companies.CareersPlatform;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** Open roles from a company's Ashby board (jobs.ashbyhq.com/{board}). No key needed. */
@Component
public class AshbyBoardClient implements CareersBoardClient {

    private final RestClient http;
    private final TtlCache<String, List<JobPosting>> cache;

    public AshbyBoardClient(RestClient.Builder builder, Clock clock) {
        this.http = builder.baseUrl("https://api.ashbyhq.com").build();
        this.cache = new TtlCache<>(Duration.ofHours(1), clock, 200);
    }

    @Override public CareersPlatform platform() { return CareersPlatform.ASHBY; }

    @Override
    public List<JobPosting> openings(String boardId, String companyName) {
        // The name is part of the key: users can name the same company differently.
        return cache.get(boardId + "|" + companyName, () -> fetch(boardId, companyName));
    }

    private List<JobPosting> fetch(String boardId, String companyName) {
        var body = http.get()
                .uri("/posting-api/job-board/{board}?includeCompensation=true", boardId)
                .retrieve().body(JsonNode.class);
        var postings = new ArrayList<JobPosting>();
        for (var job : body.path("jobs").values()) {
            if (job.path("isListed").isBoolean() && !job.path("isListed").asBoolean()) continue;   // unlisted roles

            var published = Feeds.text(job, "publishedAt");    // ISO with offset, e.g. 2026-10-20T15:04:05.123+00:00
            var description = Feeds.text(job, "descriptionPlain");
            var salary = salaryComponent(job.path("compensation"));

            var tags = new ArrayList<String>();
            for (var field : List.of("employmentType", "department", "team")) {
                var value = Feeds.text(job, field);
                if (value != null) tags.add(value);
            }

            postings.add(new JobPosting(
                    "watchlist",
                    "ashby:" + Feeds.text(job, "id"),
                    Feeds.text(job, "title"),
                    companyName,
                    Feeds.text(job, "location"),
                    job.path("isRemote").asBoolean(false),
                    Feeds.text(job, "jobUrl"),
                    published == null ? null : OffsetDateTime.parse(published).toInstant(),
                    null,
                    salary == null ? null : number(salary.path("minValue")),
                    salary == null ? null : number(salary.path("maxValue")),
                    salary == null ? null : Feeds.text(salary, "currencyCode"),
                    salary == null ? null : period(Feeds.text(salary, "interval")),
                    Feeds.text(job.path("compensation"), "compensationTierSummary"),   // e.g. "$150K – $190K • Offers Equity"
                    tags,
                    Feeds.snippet(description),
                    Feeds.description(description)));
        }
        return postings;
    }

    /** The salary part of Ashby's compensation summary, if the company shared one. */
    private static JsonNode salaryComponent(JsonNode compensation) {
        for (var part : compensation.path("summaryComponents").values()) {
            if ("Salary".equalsIgnoreCase(Feeds.text(part, "compensationType"))) return part;
        }
        return null;
    }

    /** Ashby's intervals look like "1 YEAR" or "1 HOUR". */
    private static String period(String interval) {
        if (interval == null) return null;
        if (interval.endsWith("YEAR")) return "year";
        if (interval.endsWith("MONTH")) return "month";
        if (interval.endsWith("HOUR")) return "hour";
        return null;
    }

    private static Integer number(JsonNode node) {
        return node.isNumber() ? (int) Math.round(node.asDouble()) : null;
    }
}