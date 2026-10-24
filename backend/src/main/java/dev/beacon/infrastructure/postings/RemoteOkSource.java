package dev.beacon.infrastructure.postings;

import dev.beacon.application.postings.JobPosting;
import dev.beacon.application.postings.JobSource;
import dev.beacon.application.postings.PostingMatcher;
import dev.beacon.application.postings.PostingQuery;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** Remote jobs from remoteok.com. The feed has no search, so it is cached and filtered locally. */
@Component
public class RemoteOkSource implements JobSource {

    private static final Duration FEED_TTL = Duration.ofHours(1);

    private final RestClient http;
    private final TtlCache<String, List<JobPosting>> feed;

    public RemoteOkSource(RestClient.Builder builder, Clock clock) {
        this.http = builder.baseUrl("https://remoteok.com").build();
        this.feed = new TtlCache<>(FEED_TTL, clock, 1);
    }

    @Override public String id() { return "remoteok"; }
    @Override public String name() { return "Remote OK"; }
    @Override public String coverage() { return "Remote jobs across tech and non-tech roles."; }
    @Override public String attributionUrl() { return "https://remoteok.com"; }

    @Override
    public List<JobPosting> search(PostingQuery query) {
        return feed.get("all", this::fetchAll).stream()
                .filter(p -> PostingMatcher.matches(p, query))
                .toList();
    }

    private List<JobPosting> fetchAll() {
        var body = http.get().uri("/api").retrieve().body(JsonNode.class);
        var postings = new ArrayList<JobPosting>();
        for (var job : body.values()) {
            // The first element is their legal notice, not a job.
            if (Feeds.text(job, "position") == null) continue;

            var date = Feeds.text(job, "date");
            postings.add(new JobPosting(
                    id(),
                    Feeds.text(job, "id"),
                    Feeds.plain(Feeds.text(job, "position")),
                    Feeds.text(job, "company"),
                    Feeds.text(job, "location"),
                    true,
                    Feeds.text(job, "url"),
                    date == null ? null : OffsetDateTime.parse(date).toInstant(),
                    null,
                    Feeds.positiveInt(job, "salary_min"),
                    Feeds.positiveInt(job, "salary_max"),
                    "USD",
                    "year",
                    null,
                    Feeds.strings(job, "tags"),
                    Feeds.snippet(Feeds.text(job, "description"))
            ));
        }
        return postings;
    }
}
