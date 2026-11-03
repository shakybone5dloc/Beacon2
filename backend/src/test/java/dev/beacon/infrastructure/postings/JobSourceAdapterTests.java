package dev.beacon.infrastructure.postings;

import dev.beacon.application.postings.JobSourceException;
import dev.beacon.application.postings.PostingQuery;
import dev.beacon.config.PostingsProperties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
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

    private static final PostingsProperties PROPERTIES = new PostingsProperties(null, null);
    private static final Map<String, String> USAJOBS_KEY = Map.of("apiKey", "k1", "email", "me@example.com");

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

            var results = new RemotiveSource(builder, new MutableClock()).search(JAVA, Map.of());

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

            source.search(JAVA, Map.of());
            source.search(new PostingQuery("javascript", "", false), Map.of());
            clock.advance(Duration.ofHours(5));
            source.search(JAVA, Map.of());
            clock.advance(Duration.ofHours(2));
            source.search(JAVA, Map.of());

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

            var results = new RemoteOkSource(builder, new MutableClock()).search(JAVA, Map.of());

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

            var results = new JobicySource(builder, new MutableClock()).search(new PostingQuery("java", "USA", false), Map.of());

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

            var results = new HimalayasSource(builder, new MutableClock())
                    .search(new PostingQuery("Java", "USA", false), Map.of());

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
            new HimalayasSource(builder, new MutableClock()).search(JAVA, Map.of("apiKey", "secret"));

            server.verify();
        }
    }

    @Nested
    class Adzuna {

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
            var results = new AdzunaSource(builder, new MutableClock(), PROPERTIES)
                    .search(new PostingQuery("java", "Denver", true), Map.of("appId", "id1", "appKey", "key1"));

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
            var results = new UsaJobsSource(builder, new MutableClock()).search(JAVA, USAJOBS_KEY);

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
            var results = new UsaJobsSource(builder, new MutableClock()).search(new PostingQuery("java", "", true), USAJOBS_KEY);

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

    @Nested
    class GoogleJobs {

        @Test
        void putsRemoteAndLocationIntoTheQueryAndPrefersTheDirectApplyLink() {
            var builder = RestClient.builder();
            var server = MockRestServiceServer.bindTo(builder).build();
            server.expect(requestTo(startsWith("https://serpapi.com/search.json")))
                    .andExpect(queryParam("engine", "google_jobs"))
                    .andExpect(queryParam("q", "java%20remote%20in%20Denver"))
                    .andExpect(queryParam("api_key", "serp1"))
                    .andRespond(withSuccess("""
                            {"jobs_results": [
                              {"title": "Java Engineer", "company_name": "Initech", "location": "Anywhere",
                               "via": "via LinkedIn", "share_link": "https://www.google.com/search?jobs=1",
                               "description": "Build services with Java and Spring.",
                               "detected_extensions": {"posted_at": "3 days ago", "salary": "$120K–$150K a year",
                                                       "schedule_type": "Full-time"},
                               "apply_options": [{"title": "LinkedIn", "link": "https://www.linkedin.com/jobs/view/1"}],
                               "job_id": "g1"},
                              {"title": "Java Developer", "company_name": "Globex", "location": "Denver, CO",
                               "share_link": "https://www.google.com/search?jobs=2",
                               "detected_extensions": {"posted_at": "30+ days ago", "salary": "45–55 an hour"},
                               "job_id": "g2"}
                            ]}
                            """, MediaType.APPLICATION_JSON));
            var clock = new MutableClock();

            var results = new GoogleJobsSource(builder, clock)
                    .search(new PostingQuery("java", "Denver", true), Map.of("apiKey", "serp1"));

            assertThat(results).hasSize(2);
            assertThat(results.get(0)).satisfies(p -> {
                assertThat(p.source()).isEqualTo("googlejobs");
                assertThat(p.id()).isEqualTo("g1");
                assertThat(p.company()).isEqualTo("Initech");
                assertThat(p.remote()).isTrue();
                assertThat(p.url()).isEqualTo("https://www.linkedin.com/jobs/view/1");
                assertThat(p.postedAt()).isEqualTo(clock.instant().minus(Duration.ofDays(3)));
                assertThat(p.salaryMin()).isEqualTo(120_000);
                assertThat(p.salaryMax()).isEqualTo(150_000);
                assertThat(p.salaryCurrency()).isEqualTo("USD");
                assertThat(p.salaryPeriod()).isEqualTo("year");
                assertThat(p.tags()).containsExactly("LinkedIn", "Full-time");
                assertThat(p.description()).isEqualTo("Build services with Java and Spring.");
            });
            assertThat(results.get(1)).satisfies(p -> {
                assertThat(p.remote()).isFalse();
                assertThat(p.url()).isEqualTo("https://www.google.com/search?jobs=2");
                assertThat(p.postedAt()).isEqualTo(clock.instant().minus(Duration.ofDays(30)));
                // No currency sign, so the pay stays as Google wrote it.
                assertThat(p.salaryMin()).isNull();
                assertThat(p.salaryText()).isEqualTo("45–55 an hour");
            });
            server.verify();
        }

        @Test
        void treatsNoResultsAsEmptyButOtherErrorsAsFailures() {
            var builder = RestClient.builder();
            var server = MockRestServiceServer.bindTo(builder).build();
            server.expect(requestTo(startsWith("https://serpapi.com/search.json")))
                    .andRespond(withSuccess("""
                            {"error": "Google hasn't returned any results for this query."}
                            """, MediaType.APPLICATION_JSON));
            server.expect(requestTo(startsWith("https://serpapi.com/search.json")))
                    .andRespond(withSuccess("""
                            {"error": "Your account has run out of searches."}
                            """, MediaType.APPLICATION_JSON));
            var source = new GoogleJobsSource(builder, new MutableClock());

            assertThat(source.search(new PostingQuery("cobol wizard", "", false), Map.of("apiKey", "k"))).isEmpty();
            assertThatThrownBy(() -> source.search(JAVA, Map.of("apiKey", "k")))
                    .isInstanceOf(JobSourceException.class)
                    .hasMessage("Your account has run out of searches.");
            server.verify();
        }

        @Test
        void passesOnSerpApisExplanationWhenItAnswersWithAnErrorStatus() {
            var builder = RestClient.builder();
            var server = MockRestServiceServer.bindTo(builder).build();
            server.expect(requestTo(startsWith("https://serpapi.com/search.json")))
                    .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).contentType(MediaType.APPLICATION_JSON)
                            .body("""
                                    {"error": "Your account has run out of searches."}
                                    """));
            server.expect(requestTo(startsWith("https://serpapi.com/search.json")))
                    .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                            .body("""
                                    {"error": "Invalid API key."}
                                    """));
            var source = new GoogleJobsSource(builder, new MutableClock());

            assertThatThrownBy(() -> source.search(JAVA, Map.of("apiKey", "k")))
                    .isInstanceOf(JobSourceException.class)
                    .hasMessage("Your account has run out of searches.");
            // A rejected key stays an HTTP error, so the search says to check the key.
            assertThatThrownBy(() -> source.search(new PostingQuery("kotlin", "", false), Map.of("apiKey", "k")))
                    .isInstanceOf(HttpClientErrorException.Unauthorized.class);
            server.verify();
        }

        @Test
        void triesOnceMoreWhenTheConnectionDropsButNotWhenItIsSlow() {
            var builder = RestClient.builder();
            var server = MockRestServiceServer.bindTo(builder).build();
            server.expect(requestTo(startsWith("https://serpapi.com/search.json")))
                    .andRespond(request -> { throw new SocketException("Connection reset"); });
            server.expect(requestTo(startsWith("https://serpapi.com/search.json")))
                    .andRespond(withSuccess("""
                            {"jobs_results": [{"title": "Java Engineer", "company_name": "Initech", "job_id": "g1"}]}
                            """, MediaType.APPLICATION_JSON));
            server.expect(once(), requestTo(startsWith("https://serpapi.com/search.json")))
                    .andRespond(request -> { throw new SocketTimeoutException("Read timed out"); });
            var source = new GoogleJobsSource(builder, new MutableClock());

            assertThat(source.search(JAVA, Map.of("apiKey", "k"))).hasSize(1);
            assertThatThrownBy(() -> source.search(new PostingQuery("kotlin", "", false), Map.of("apiKey", "k")))
                    .isInstanceOf(ResourceAccessException.class);
            server.verify();
        }

        @Test
        void readsGooglesSalaryLines() {
            assertThat(GoogleJobsSource.Salary.parse("$25 an hour"))
                    .isEqualTo(new GoogleJobsSource.Salary(25, 25, "USD", "hour", null));
            assertThat(GoogleJobsSource.Salary.parse("$95,000–$110,000 a year"))
                    .isEqualTo(new GoogleJobsSource.Salary(95_000, 110_000, "USD", "year", null));
            assertThat(GoogleJobsSource.Salary.parse("€60K a year").text()).isEqualTo("€60K a year");
            assertThat(GoogleJobsSource.Salary.parse(null).text()).isNull();
        }
    }

    @Nested
    class JSearch {

        @Test
        void sendsTheRapidApiHeadersAndReadsParsedFields() {
            var builder = RestClient.builder();
            var server = MockRestServiceServer.bindTo(builder).build();
            server.expect(requestTo(startsWith("https://jsearch.p.rapidapi.com/search")))
                    .andExpect(header("X-RapidAPI-Key", "rapid1"))
                    .andExpect(header("X-RapidAPI-Host", "jsearch.p.rapidapi.com"))
                    .andExpect(queryParam("query", "java%20in%20Denver"))
                    .andExpect(queryParam("num_pages", "1"))
                    .andExpect(queryParam("work_from_home", "true"))
                    .andRespond(withSuccess("""
                            {"status": "OK", "data": [
                              {"job_id": "j1", "job_title": "Java Developer", "employer_name": "Initech",
                               "job_publisher": "Indeed", "job_employment_type": "FULLTIME",
                               "job_apply_link": "https://www.indeed.com/viewjob?jk=1",
                               "job_description": "Java, Spring, Postgres.", "job_is_remote": true,
                               "job_posted_at_timestamp": 1792000000, "job_offer_expiration_timestamp": 1794700800,
                               "job_city": "Denver", "job_state": "CO", "job_country": "US",
                               "job_min_salary": 110000, "job_max_salary": 140000, "job_salary_period": "YEAR"}
                            ]}
                            """, MediaType.APPLICATION_JSON));

            var results = new JSearchSource(builder, new MutableClock())
                    .search(new PostingQuery("java", "Denver", true), Map.of("apiKey", "rapid1"));

            assertThat(results).singleElement().satisfies(p -> {
                assertThat(p.source()).isEqualTo("jsearch");
                assertThat(p.company()).isEqualTo("Initech");
                assertThat(p.location()).isEqualTo("Denver, CO, US");
                assertThat(p.remote()).isTrue();
                assertThat(p.url()).isEqualTo("https://www.indeed.com/viewjob?jk=1");
                assertThat(p.postedAt()).isEqualTo(Instant.ofEpochSecond(1792000000));
                assertThat(p.closesOn()).isEqualTo(LocalDate.parse("2026-11-15"));
                assertThat(p.salaryMin()).isEqualTo(110_000);
                assertThat(p.salaryCurrency()).isEqualTo("USD");
                assertThat(p.salaryPeriod()).isEqualTo("year");
                assertThat(p.tags()).containsExactly("Indeed", "Full-time");
            });
            server.verify();
        }
    }
}
