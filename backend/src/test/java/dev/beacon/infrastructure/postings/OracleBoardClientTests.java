package dev.beacon.infrastructure.postings;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Against canned responses shaped like the JSON an Oracle Recruiting Cloud careers site loads. */
class OracleBoardClientTests {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneOffset.UTC);
    private static final String API = "https://eeho.fa.us2.oraclecloud.com/hcmRestApi/resources/latest";

    @Test
    void listsTheNewestRolesForTheCompanyPage() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo(API + "/recruitingCEJobRequisitions?onlyData=true&expand=requisitionList"
                        + "&finder=findReqs;siteNumber=CX_45001,limit=20,sortBy=POSTING_DATES_DESC"))
                .andRespond(withSuccess("""
                        {"items": [{"TotalJobsCount": 2725, "requisitionList": [
                          {"Id": "344708", "Title": "Principal Software Engineer", "PostedDate": "2026-11-01",
                           "PostingEndDate": null, "PrimaryLocation": "Nashville, TN, United States",
                           "ShortDescriptionStr": "Principal Platform Software Engineer", "WorkplaceType": ""}]}]}
                        """, MediaType.APPLICATION_JSON));

        var postings = new OracleBoardClient(builder, CLOCK).openings("eeho.fa.us2/CX_45001", "Oracle");

        server.verify();
        assertThat(postings).singleElement().satisfies(p -> {
            assertThat(p.source()).isEqualTo("watchlist");
            assertThat(p.id()).isEqualTo("oracle:344708");
            assertThat(p.company()).isEqualTo("Oracle");
            assertThat(p.location()).isEqualTo("Nashville, TN, United States");
            assertThat(p.remote()).isFalse();
            assertThat(p.url()).isEqualTo("https://eeho.fa.us2.oraclecloud.com/hcmUI/CandidateExperience/en/sites/CX_45001/job/344708");
            assertThat(p.postedAt()).isEqualTo(Instant.parse("2026-11-01T12:00:00Z"));
            assertThat(p.snippet()).isEqualTo("Principal Platform Software Engineer");
        });
    }

    @Test
    void searchesOnOracleAndFetchesEachResultsDescription() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo(API + "/recruitingCEJobRequisitions?onlyData=true&expand=requisitionList"
                        + "&finder=findReqs;siteNumber=CX_45001,limit=20,sortBy=POSTING_DATES_DESC,keyword=java%20cloud"))
                .andRespond(withSuccess("""
                        {"items": [{"requisitionList": [
                          {"Id": "344708", "Title": "Principal Software Engineer", "PostedDate": "2026-11-01",
                           "PrimaryLocation": "Remote, United States"}]}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(API + "/recruitingCEJobRequisitionDetails?expand=all&onlyData=true"
                        + "&finder=ById;Id=%22344708%22,siteNumber=CX_45001"))
                .andRespond(withSuccess("""
                        {"items": [{"Id": "344708",
                          "ExternalDescriptionStr": "<p>OCI delivers mission-critical cloud.</p>",
                          "ExternalResponsibilitiesStr": "<ul><li>Build Java services</li></ul>",
                          "ExternalQualificationsStr": "<p>8+ years</p>",
                          "ExternalPostedEndDate": "2026-12-01T00:00:00+00:00"}]}
                        """, MediaType.APPLICATION_JSON));

        // Commas and semicolons would break Oracle's finder, so they're dropped from the keywords.
        var postings = new OracleBoardClient(builder, CLOCK).search("eeho.fa.us2/CX_45001", "Oracle", "java; cloud");

        server.verify();
        assertThat(postings).singleElement().satisfies(p -> {
            assertThat(p.remote()).isTrue();
            assertThat(p.closesOn()).isEqualTo(LocalDate.of(2026, 12, 1));
            assertThat(p.description()).contains("OCI delivers mission-critical cloud.")
                    .contains("Build Java services").contains("8+ years");
        });
    }
}
