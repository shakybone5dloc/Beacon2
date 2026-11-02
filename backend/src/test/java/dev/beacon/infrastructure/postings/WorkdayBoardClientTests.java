package dev.beacon.infrastructure.postings;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Against canned responses shaped like the JSON a Workday careers site loads. */
class WorkdayBoardClientTests {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneOffset.UTC);
    private static final String API = "https://acme.wd1.myworkdayjobs.com/wday/cxs/acme/External";

    @Test
    void listsTheNewestRolesForTheCompanyPage() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo(API + "/jobs"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.searchText").value(""))
                .andExpect(jsonPath("$.limit").value(20))
                .andRespond(withSuccess("""
                        {"total": 2, "jobPostings": [
                          {"title": "Staff Engineer", "externalPath": "/job/IN-INDIANAPOLIS/Staff-Engineer_JR1",
                           "locationsText": "3 Locations", "postedOn": "Posted Yesterday", "bulletFields": ["JR1"]},
                          {"title": "Analyst", "externalPath": "/job/Remote/Analyst_JR2",
                           "locationsText": "Remote", "postedOn": "Posted 30+ Days Ago", "bulletFields": ["JR2"]}]}
                        """, MediaType.APPLICATION_JSON));

        var postings = new WorkdayBoardClient(builder, CLOCK).openings("acme.wd1/External", "Acme");

        server.verify();
        assertThat(postings).hasSize(2);
        assertThat(postings.getFirst()).satisfies(p -> {
            assertThat(p.source()).isEqualTo("watchlist");
            assertThat(p.id()).isEqualTo("workday:/job/IN-INDIANAPOLIS/Staff-Engineer_JR1");
            assertThat(p.company()).isEqualTo("Acme");
            assertThat(p.url()).isEqualTo("https://acme.wd1.myworkdayjobs.com/External/job/IN-INDIANAPOLIS/Staff-Engineer_JR1");
            assertThat(p.postedAt()).isEqualTo(Instant.parse("2026-10-31T12:00:00Z"));
            assertThat(p.tags()).containsExactly("JR1");
        });
        // "30+ days" is too vague to date.
        assertThat(postings.get(1).postedAt()).isNull();
        assertThat(postings.get(1).remote()).isTrue();
    }

    @Test
    void searchesOnWorkdayAndFetchesEachResultsDescription() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo(API + "/jobs"))
                .andExpect(jsonPath("$.searchText").value("java"))
                .andRespond(withSuccess("""
                        {"total": 1, "jobPostings": [
                          {"title": "Staff Engineer", "externalPath": "/job/IN-INDIANAPOLIS/Staff-Engineer_JR1",
                           "locationsText": "3 Locations", "postedOn": "Posted 2 Days Ago"}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(API + "/job/IN-INDIANAPOLIS/Staff-Engineer_JR1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"jobPostingInfo": {"title": "Staff Engineer",
                          "jobDescription": "<p>Build <b>Java</b> services.</p>",
                          "location": "IN-INDIANAPOLIS, 220 VIRGINIA AVE",
                          "additionalLocations": ["GA-ATLANTA", "OH-MASON"],
                          "startDate": "2026-10-29", "endDate": "2026-11-13",
                          "externalUrl": "https://acme.wd1.myworkdayjobs.com/External/job/IN-INDIANAPOLIS/Staff-Engineer_JR1"}}
                        """, MediaType.APPLICATION_JSON));
        var client = new WorkdayBoardClient(builder, CLOCK);

        var postings = client.search("acme.wd1/External", "Acme", "java");
        // Both calls are kept for the hour: the server expects each once.
        client.search("acme.wd1/External", "Acme", "java");

        server.verify();
        assertThat(postings).singleElement().satisfies(p -> {
            assertThat(p.location()).isEqualTo("IN-INDIANAPOLIS, 220 VIRGINIA AVE + 2 more");
            assertThat(p.postedAt()).isEqualTo(Instant.parse("2026-10-29T12:00:00Z"));
            assertThat(p.closesOn()).isEqualTo(LocalDate.of(2026, 11, 13));
            assertThat(p.description()).contains("Build Java services.");
        });
    }

    @Test
    void aFailedDetailCallKeepsTheSearchResult() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo(API + "/jobs"))
                .andRespond(withSuccess("""
                        {"jobPostings": [{"title": "Staff Engineer", "externalPath": "/job/X/Staff_JR1", "postedOn": "Posted Today"}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(API + "/job/X/Staff_JR1"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        var postings = new WorkdayBoardClient(builder, CLOCK).search("acme.wd1/External", "Acme", "java");

        assertThat(postings).singleElement().satisfies(p -> {
            assertThat(p.title()).isEqualTo("Staff Engineer");
            assertThat(p.postedAt()).isEqualTo(Instant.parse("2026-11-01T12:00:00Z"));
            assertThat(p.description()).isNull();
        });
    }
}
