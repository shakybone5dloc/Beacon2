package dev.beacon.infrastructure.postings;

import dev.beacon.domain.companies.CareersBoard;
import dev.beacon.domain.companies.CareersPlatform;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CareersPageFinderTests {

    @Test
    void readsABoardLinkWithoutOpeningIt() {
        assertThat(new CareersPageFinder().find("https://jobs.lever.co/acme"))
                .contains(new CareersBoard(CareersPlatform.LEVER, "acme"));
    }

    @Test
    void findsTheBoardACompanysOwnPageLinksTo() {
        var html = """
                <a href="https://www.linkedin.com/company/acme">LinkedIn</a>
                <script src="https://boards.greenhouse.io/embed/job_board/js?for=acme&amp;b=x"></script>
                """;

        assertThat(CareersPageFinder.scan(html)).contains(new CareersBoard(CareersPlatform.GREENHOUSE, "acme"));
    }

    @Test
    void findsAWorkdayLinkInsideEscapedJson() {
        var html = "{\"jobsUrl\":\"https:\\/\\/acme.wd5.myworkdayjobs.com\\/en-US\\/External\"}";

        assertThat(CareersPageFinder.scan(html)).contains(new CareersBoard(CareersPlatform.WORKDAY, "acme.wd5/External"));
    }

    @Test
    void findsTheOracleSiteACustomDomainLoadsFrom() {
        // careers.oracle.com: the page names its Oracle Cloud host and site number, not a board link.
        var html = """
                <link rel="icon" href="https://eeho.fa.us2.oraclecloud.com:443/hcmRestApi/CandidateExperience/siteFavicon/favicon-16x16.png?siteNumber=CX_45001&size=16x16">
                """;

        assertThat(CareersPageFinder.scan(html)).contains(CareersBoard.oracle("eeho.fa.us2", "CX_45001"));
    }

    @Test
    void findsNothingOnAnOrdinaryPage() {
        assertThat(CareersPageFinder.scan("<a href=\"https://acme.example/about\">About</a>")).isEmpty();
    }

    @Test
    void neverOpensAPrivateAddress() {
        assertThat(new CareersPageFinder().find("http://127.0.0.1:8080/careers")).isEmpty();
    }
}
