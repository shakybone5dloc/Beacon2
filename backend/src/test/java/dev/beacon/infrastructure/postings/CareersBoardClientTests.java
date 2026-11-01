package dev.beacon.infrastructure.postings;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Each careers-board client against a canned response shaped like the real API's. */
class CareersBoardClientTests {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void greenhouse() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("https://boards-api.greenhouse.io/v1/boards/acme/jobs?content=true"))
                .andRespond(withSuccess("""
                        {"jobs": [{"id": 4011, "title": "Senior Java Engineer",
                          "absolute_url": "https://boards.greenhouse.io/acme/jobs/4011",
                          "location": {"name": "Remote - US"}, "updated_at": "2026-10-28T10:15:00-04:00",
                          "content": "&lt;p&gt;Build &lt;b&gt;payments&lt;/b&gt; services.&lt;/p&gt;"}],
                         "meta": {"total": 1}}
                        """, MediaType.APPLICATION_JSON));
        var client = new GreenhouseBoardClient(builder, CLOCK);

        var postings = client.openings("acme", "Acme");
        // Cached: a second read in the hour doesn't call Greenhouse again (the server expects one call).
        client.openings("acme", "Acme");

        server.verify();
        assertThat(postings).singleElement().satisfies(p -> {
            assertThat(p.source()).isEqualTo("watchlist");
            assertThat(p.id()).isEqualTo("greenhouse:4011");
            assertThat(p.title()).isEqualTo("Senior Java Engineer");
            assertThat(p.company()).isEqualTo("Acme");
            assertThat(p.location()).isEqualTo("Remote - US");
            assertThat(p.remote()).isTrue();
            assertThat(p.url()).isEqualTo("https://boards.greenhouse.io/acme/jobs/4011");
            assertThat(p.postedAt()).isEqualTo(Instant.parse("2026-10-28T14:15:00Z"));
            assertThat(p.description()).contains("Build payments services.");
        });
    }

    @Test
    void lever() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("https://api.lever.co/v0/postings/acme?mode=json"))
                .andRespond(withSuccess("""
                        [{"id": "4f6a-11", "text": "Staff Engineer, Identity",
                          "hostedUrl": "https://jobs.lever.co/acme/4f6a-11",
                          "categories": {"location": "Indianapolis, IN", "commitment": "Full-time", "team": "Platform"},
                          "workplaceType": "hybrid", "createdAt": 1761300000000,
                          "descriptionPlain": "Own our identity platform.",
                          "salaryRange": {"min": 150000, "max": 185000, "currency": "USD", "interval": "per-year-salary"}}]
                        """, MediaType.APPLICATION_JSON));

        var postings = new LeverBoardClient(builder, CLOCK).openings("acme", "Acme");

        assertThat(postings).singleElement().satisfies(p -> {
            assertThat(p.id()).isEqualTo("lever:4f6a-11");
            assertThat(p.title()).isEqualTo("Staff Engineer, Identity");
            assertThat(p.location()).isEqualTo("Indianapolis, IN");
            assertThat(p.remote()).isFalse();
            assertThat(p.postedAt()).isEqualTo(Instant.ofEpochMilli(1761300000000L));
            assertThat(p.salaryMin()).isEqualTo(150000);
            assertThat(p.salaryMax()).isEqualTo(185000);
            assertThat(p.salaryPeriod()).isEqualTo("year");
            assertThat(p.tags()).containsExactly("Full-time", "Platform");
        });
    }

    @Test
    void ashbyLeavesOutUnlistedRoles() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("https://api.ashbyhq.com/posting-api/job-board/acme?includeCompensation=true"))
                .andRespond(withSuccess("""
                        {"jobs": [
                          {"id": "9b1c", "title": "Backend Engineer", "location": "Remote", "isRemote": true,
                           "isListed": true, "jobUrl": "https://jobs.ashbyhq.com/acme/9b1c",
                           "publishedAt": "2026-10-30T09:00:00.000+00:00", "employmentType": "FullTime",
                           "descriptionPlain": "Build the API.",
                           "compensation": {"compensationTierSummary": "$140K – $170K",
                             "summaryComponents": [
                               {"compensationType": "EquityPercentage", "minValue": 0.1, "maxValue": 0.2},
                               {"compensationType": "Salary", "interval": "1 YEAR", "currencyCode": "USD",
                                "minValue": 140000, "maxValue": 170000}]}},
                          {"id": "0000", "title": "Internal only", "isListed": false, "jobUrl": "https://jobs.ashbyhq.com/acme/0000"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        var postings = new AshbyBoardClient(builder, CLOCK).openings("acme", "Acme");

        assertThat(postings).singleElement().satisfies(p -> {
            assertThat(p.id()).isEqualTo("ashby:9b1c");
            assertThat(p.remote()).isTrue();
            assertThat(p.postedAt()).isEqualTo(Instant.parse("2026-10-30T09:00:00Z"));
            assertThat(p.salaryMin()).isEqualTo(140000);
            assertThat(p.salaryMax()).isEqualTo(170000);
            assertThat(p.salaryCurrency()).isEqualTo("USD");
            assertThat(p.salaryPeriod()).isEqualTo("year");
            assertThat(p.salaryText()).isEqualTo("$140K – $170K");
        });
    }
}
