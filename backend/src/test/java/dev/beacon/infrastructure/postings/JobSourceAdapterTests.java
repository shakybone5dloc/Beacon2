package dev.beacon.infrastructure.postings;

import dev.beacon.application.postings.PostingQuery;
import dev.beacon.config.PostingsProperties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;

/** Each adapter against a canned response shaped like the real API's. */
class JobSourceAdapterTests {

    private static final PostingQuery JAVA = new PostingQuery("java", "", false);

    /** A clock tests can move forward to expire caches. */
    static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-24T12:00:00Z");

        void advance(Duration by) { now = now.plus(by); }
        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }

    private static PostingsProperties properties(PostingsProperties.Adzuna adzuna, PostingsProperties.UsaJobs usajobs) {
        return new PostingsProperties(null, adzuna, usajobs, null);
    }

    @Nested
    class Remotive {

        static final String FEED = """
                {"0-legal-notice": "...", "job-count": 2, "jobs": [
                  {"id": 1, "url": "https://remotive.com/remote-jobs/1", "title": "Senior Java Engineer",
                   "company_name": "Acme", "category": "Software Development", "tags": ["spring"],
                   "publication_date": "2026-10-22T18:40:19", "candidate_required_location": "USA",
                   "salary": "$150k - $180k", "description": "<p>Build <b>things</b> &amp; more</p>"},
                  {"id": 2, "url": "https://remotive.com/remote-jobs/2", "title": "JavaScript Developer",
                   "company_name": "Globex", "category": "Software Development", "tags": [],
                   "publication_date": "2026-10-21T08:00:00", "candidate_required_location": "Worldwide",
                   "salary": "", "description": ""}
                ]}
                """;

        @Test
        void mapsTheFeedAndFiltersLocally() {
            var builder = RestClient.builder();
            var server = MockRestServiceServer.bindTo(builder).build();
            server.expect(once(), requestTo("https://remotive.com/api/remote-jobs"))
                    .andRespond(withSuccess(FEED, MediaType.APPLICATION_JSON));

            var results = new RemotiveSource(builder, new MutableClock()).search(JAVA);

            assertThat(results).singleElement().satisfies(p -> {
                assertThat(p.source()).isEqualTo("remotive");
                assertThat(p.title()).isEqualTo("Senior Java Engineer");
                assertThat(p.company()).isEqualTo("Acme");
                assertThat(p.location()).isEqualTo("USA");
                assertThat(p.remote()).isTrue();
                assertThat(p.url()).isEqualTo("https://remotive.com/remote-jobs/1");
                assertThat(p.postedAt()).isEqualTo(Instant.parse("2026-10-22T18:40:19Z"));
                assertThat(p.salaryText()).isEqualTo("$150k - $180k");
                assertThat(p.tags()).contains("spring", "Software Development");
                assertThat(p.snippet()).isEqualTo("Build things & more");
                assertThat(p.description()).isEqualTo("Build things & more");
            });
            server.verify();
        }

        @Test
        void reusesTheFeedUntilItExpires() {
            var builder = RestClient.builder();
            var server = MockRestServiceServer.bindTo(builder).build();
            server.expect(once(), requestTo("https://remotive.com/api/remote-jobs"))
                    .andRespond(withSuccess(FEED, MediaType.APPLICATION_JSON));
            server.expect(once(), requestTo("https://remotive.com/api/remote-jobs"))
                    .andRespond(withSuccess(FEED, MediaType.APPLICATION_JSON));
            var clock = new MutableClock();
            var source = new RemotiveSource(builder, clock);

            source.search(JAVA);
            source.search(new PostingQuery("javascript", "", false));
            clock.advance(Duration.ofHours(5));
            source.search(JAVA);
            clock.advance(Duration.ofHours(2));
            source.search(JAVA);

            server.verify();
        }
    }

    @Nested
    class RemoteOk {

        @Test
        void skipsTheLegalNoticeAndTreatsZeroSalaryAsUnknown() {
            var builder = RestClient.builder();
            var server = MockRestServiceServer.bindTo(builder).build();
            server.expect(requestTo("https://remoteok.com/api")).andRespond(withSuccess("""
                    [{"legal": "API Terms of Service: Please link back"},
                     {"id": "11", "date": "2026-10-23T16:00:12+00:00", "company": "B2spin",
                      "position": "Senior Java Engineer", "tags": ["java", "backend"], "location": "",
                      "salary_min": 0, "salary_max": 0, "description": "",
                      "url": "https://remoteOK.com/remote-jobs/11"},
                     {"id": "12", "date": "2026-10-22T12:58:28+00:00", "company": "Lemon.io",
                      "position": "Java Developer", "tags": [], "location": "Europe",
                      "salary_min": 50000, "salary_max": 150000, "description": "",
                      "url": "https://remoteOK.com/remote-jobs/12"}]
                    """, MediaType.APPLICATION_JSON));

            var results = new RemoteOkSource(builder, new MutableClock()).search(JAVA);

            assertThat(results).hasSize(2);
            assertThat(results.get(0).location()).isNull();
            assertThat(results.get(0).salaryMin()).isNull();
            assertThat(results.get(1).salaryMin()).isEqualTo(50000);
            assertThat(results.get(1).salaryMax()).isEqualTo(150000);
            assertThat(results.get(1).postedAt()).isEqualTo(Instant.parse("2026-10-22T12:58:28Z"));
        }
    }

    @Nested
    class Jobicy {

        @Test
        void mapsJobsAndFiltersByLocation() {
            var builder = RestClient.builder();
            var server = MockRestServiceServer.bindTo(builder).build();
            server.expect(requestTo("https://jobicy.com/api/jobs?count=200")).andRespond(withSuccess("""
                    {"jobs": [
                      {"id": 1, "url": "https://jobicy.com/jobs/1", "jobTitle": "Java Engineer",
                       "companyName": "GitLab", "jobIndustry": ["Engineering"], "jobType": ["Full-Time"],
                       "jobGeo": "USA", "pubDate": "2026-10-24T13:21:07+00:00",
                       "salaryMin": 120000, "salaryMax": 160000, "salaryCurrency": "USD", "salaryPeriod": "yearly",
                       "jobExcerpt": "Join us&hellip;"},
                      {"id": 2, "url": "https://jobicy.com/jobs/2", "jobTitle": "Java Engineer",
                       "companyName": "Other", "jobGeo": "South Korea", "pubDate": "2026-10-24T13:21:07+00:00"}
                    ]}
                    """, MediaType.APPLICATION_JSON));

            var results = new JobicySource(builder, new MutableClock()).search(new PostingQuery("java", "USA", false));

            assertThat(results).singleElement().satisfies(p -> {
                assertThat(p.company()).isEqualTo("GitLab");
                assertThat(p.salaryPeriod()).isEqualTo("year");
                assertThat(p.salaryCurrency()).isEqualTo("USD");
                assertThat(p.tags()).containsExactly("Engineering", "Full-Time");
                assertThat(p.snippet()).isEqualTo("Join us…");
            });
        }
    }

    @Nested
    class Himalayas {

        static final String PAGE = """
                {"jobs": [
                  {"title": "Senior UI & UX Designer", "companyName": "lemon.io", "locationRestrictions": [],
                   "categories": ["UI-UX-Designer"], "parentCategories": ["Design"], "pubDate": 1792675549,
                   "expiryDate": 1795259343, "applicationLink": "https://himalayas.app/a?utm=x",
                   "guid": "https://himalayas.app/a", "excerpt": "Design things"},
                  {"title": "Senior Full Stack Java Engineer", "companyName": "Acme",
                   "locationRestrictions": ["United States", "Canada"], "categories": ["Java-Developer"],
                   "pubDate": 1792675549, "expiryDate": 1795259343, "minSalary": 140000, "maxSalary": 170000,
                   "currency": "USD", "salaryPeriod": "annual",
                   "applicationLink": "https://himalayas.app/b?utm=x", "guid": "https://himalayas.app/b"}
                ]}
                """;

        @Test
        void dropsLooseMatchesAndUsesTheApplicationLink() {
            var builder = RestClient.builder();
            var server = MockRestServiceServer.bindTo(builder).build();
            server.expect(requestTo(startsWith("https://himalayas.app/jobs/api/search")))
                    .andExpect(queryParam("q", "java"))
                    .andExpect(queryParam("page", "1"))
                    .andRespond(withSuccess(PAGE, MediaType.APPLICATION_JSON));

            var results = new HimalayasSource(builder, new MutableClock(), properties(null, null))
                    .search(new PostingQuery("Java", "USA", false));

            assertThat(results).singleElement().satisfies(p -> {
                assertThat(p.title()).isEqualTo("Senior Full Stack Java Engineer");
                assertThat(p.url()).isEqualTo("https://himalayas.app/b?utm=x");
                assertThat(p.location()).isEqualTo("United States, Canada");
                assertThat(p.closesOn()).isEqualTo(LocalDate.ofInstant(Instant.ofEpochSecond(1795259343), ZoneOffset.UTC));
                assertThat(p.salaryMin()).isEqualTo(140000);
                assertThat(p.salaryPeriod()).isEqualTo("year");
                assertThat(p.tags()).contains("Java Developer");
            });
            server.verify();
        }

        @Test
        void sendsTheOptionalKeyAsABearerToken() {
            var builder = RestClient.builder();
            var server = MockRestServiceServer.bindTo(builder).build();
            server.expect(requestTo(startsWith("https://himalayas.app/jobs/api/search")))
                    .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer secret"))
                    .andRespond(withSuccess("{\"jobs\": []}", MediaType.APPLICATION_JSON));
            var props = new PostingsProperties(null, null, null, new PostingsProperties.Himalayas("secret"));

            new HimalayasSource(builder, new MutableClock(), props).search(JAVA);

            server.verify();
        }
    }

    @Nested
    class Adzuna {

        @Test
        void isOffWithoutCredentials() {
            var source = new AdzunaSource(RestClient.builder(), new MutableClock(), properties(null, null));

            assertThat(source.isConfigured()).isFalse();
            assertThat(source.setupHint()).contains("ADZUNA_APP_ID");
        }

        @Test
        void passesTheQueryAndIgnoresPredictedSalaries() {
            var builder = RestClient.builder();
            var server = MockRestServiceServer.bindTo(builder).build();
            server.expect(requestTo(startsWith("https://api.adzuna.com/v1/api/jobs/us/search/1")))
                    .andExpect(method(HttpMethod.GET))
                    .andExpect(queryParam("app_id", "id1"))
                    .andExpect(queryParam("app_key", "key1"))
                    .andExpect(queryParam("what", "java%20remote"))
                    .andExpect(queryParam("where", "Denver"))
                    .andRespond(withSuccess("""
                            {"results": [
                              {"id": "a1", "title": "<strong>Java</strong> Developer", "created": "2026-10-20T10:12:13Z",
                               "company": {"display_name": "Acme"}, "location": {"display_name": "Denver, CO"},
                               "redirect_url": "https://www.adzuna.com/land/ad/a1", "description": "Remote role",
                               "salary_min": 90000, "salary_max": 120000, "salary_is_predicted": "1",
                               "category": {"label": "IT Jobs"}}
                            ]}
                            """, MediaType.APPLICATION_JSON));
            var props = properties(new PostingsProperties.Adzuna("id1", "key1", null), null);

            var results = new AdzunaSource(builder, new MutableClock(), props)
                    .search(new PostingQuery("java", "Denver", true));

            assertThat(results).singleElement().satisfies(p -> {
                assertThat(p.title()).isEqualTo("Java Developer");
                assertThat(p.company()).isEqualTo("Acme");
                assertThat(p.location()).isEqualTo("Denver, CO");
                assertThat(p.remote()).isTrue();
                assertThat(p.url()).isEqualTo("https://www.adzuna.com/land/ad/a1");
                assertThat(p.salaryMin()).isNull();
                assertThat(p.salaryCurrency()).isEqualTo("USD");
                assertThat(p.tags()).containsExactly("IT Jobs");
            });
            server.verify();
        }
    }

    @Nested
    class UsaJobs {

        @Test
        void isOffWithoutCredentials() {
            var source = new UsaJobsSource(RestClient.builder(), new MutableClock(), properties(null, null));

            assertThat(source.isConfigured()).isFalse();
            assertThat(source.setupHint()).contains("USAJOBS_API_KEY");
        }

        @Test
        void dropsAnnouncementsThatOnlyMentionTheKeywordInPassing() {
            var builder = RestClient.builder();
            var server = MockRestServiceServer.bindTo(builder).build();
            var longSummary = "Develop and maintain enterprise systems for the agency. ".repeat(6) + "Uses Java and Spring.";
            server.expect(requestTo(startsWith("https://data.usajobs.gov/api/Search")))
                    .andRespond(withSuccess("""
                            {"SearchResult": {"SearchResultItems": [
                              {"MatchedObjectDescriptor": {"PositionID": "P1", "PositionTitle": "IT Specialist (APPSW)",
                                "OrganizationName": "Census Bureau", "PositionURI": "https://www.usajobs.gov/job/1",
                                "UserArea": {"Details": {"JobSummary": "%s"}}}},
                              {"MatchedObjectDescriptor": {"PositionID": "P2", "PositionTitle": "Food and Beverage Attendant",
                                "OrganizationName": "Army MWR", "PositionURI": "https://www.usajobs.gov/job/2",
                                "UserArea": {"Details": {"JobSummary": "Serve food and beverages in the club."}}}}
                            ]}}
                            """.formatted(longSummary), MediaType.APPLICATION_JSON));
            var props = properties(null, new PostingsProperties.UsaJobs("k1", "me@example.com"));

            var results = new UsaJobsSource(builder, new MutableClock(), props).search(JAVA);

            // Kept even though "Java" sits past the snippet's cut-off.
            assertThat(results).singleElement().satisfies(p -> {
                assertThat(p.title()).isEqualTo("IT Specialist (APPSW)");
                assertThat(p.snippet()).doesNotContain("Java");
            });
        }

        @Test
        void sendsTheRequiredHeadersAndReadsTheCloseDate() {
            var builder = RestClient.builder();
            var server = MockRestServiceServer.bindTo(builder).build();
            server.expect(requestTo(startsWith("https://data.usajobs.gov/api/Search")))
                    .andExpect(header(HttpHeaders.USER_AGENT, "me@example.com"))
                    .andExpect(header("Authorization-Key", "k1"))
                    .andExpect(queryParam("Keyword", "java"))
                    .andExpect(queryParam("RemoteIndicator", "True"))
                    .andRespond(withSuccess("""
                            {"SearchResult": {"SearchResultItems": [
                              {"MatchedObjectDescriptor": {
                                "PositionID": "P1", "PositionTitle": "IT Specialist (Java)",
                                "OrganizationName": "Department of Veterans Affairs",
                                "PositionLocationDisplay": "Anywhere in the U.S. (remote job)",
                                "PositionURI": "https://www.usajobs.gov/job/1",
                                "PublicationStartDate": "2026-10-20T00:00:00.0000",
                                "ApplicationCloseDate": "2026-11-15T23:59:59.9970",
                                "PositionRemuneration": [{"MinimumRange": "98496.0", "MaximumRange": "128043.0", "Description": "Per Year"}],
                                "UserArea": {"Details": {"JobSummary": "<p>Maintain systems</p>"}}
                              }}
                            ]}}
                            """, MediaType.APPLICATION_JSON));
            var props = properties(null, new PostingsProperties.UsaJobs("k1", "me@example.com"));

            var results = new UsaJobsSource(builder, new MutableClock(), props).search(new PostingQuery("java", "", true));

            assertThat(results).singleElement().satisfies(p -> {
                assertThat(p.title()).isEqualTo("IT Specialist (Java)");
                assertThat(p.company()).isEqualTo("Department of Veterans Affairs");
                assertThat(p.closesOn()).isEqualTo(LocalDate.parse("2026-11-15"));
                assertThat(p.postedAt()).isEqualTo(Instant.parse("2026-10-20T00:00:00Z"));
                assertThat(p.salaryMin()).isEqualTo(98496);
                assertThat(p.salaryMax()).isEqualTo(128043);
                assertThat(p.salaryPeriod()).isEqualTo("year");
                assertThat(p.remote()).isTrue();
                assertThat(p.snippet()).isEqualTo("Maintain systems");
            });
            server.verify();
        }
    }
}
