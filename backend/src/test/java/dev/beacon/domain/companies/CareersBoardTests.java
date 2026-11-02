package dev.beacon.domain.companies;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CareersBoardTests {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "https://boards.greenhouse.io/acme                       | GREENHOUSE | acme",
            "https://job-boards.greenhouse.io/acme/jobs/123?gh_src=x | GREENHOUSE | acme",
            "  http://boards.greenhouse.io/acme_co                   | GREENHOUSE | acme_co",
            "https://jobs.lever.co/acme                              | LEVER      | acme",
            "https://jobs.lever.co/acme.io/4f6a-11/apply             | LEVER      | acme.io",
            "https://jobs.ashbyhq.com/acme                           | ASHBY      | acme",
            "https://jobs.ashbyhq.com/acme/9b1c2d3e                  | ASHBY      | acme",
            "https://boards.greenhouse.io/embed/job_board?for=acme   | GREENHOUSE | acme",
            "https://elevancehealth.wd1.myworkdayjobs.com/ANT        | WORKDAY    | elevancehealth.wd1/ANT",
            "https://ElevanceHealth.wd1.myworkdayjobs.com/en-US/ANT/job/IL-CHICAGO/Engineer_JR1 | WORKDAY | elevancehealth.wd1/ANT",
            "https://eeho.fa.us2.oraclecloud.com/hcmUI/CandidateExperience/en/sites/CX_45001/job/344708 | ORACLE | eeho.fa.us2/CX_45001",
    })
    void findsThePlatformAndBoardInACareersLink(String url, CareersPlatform platform, String boardId) {
        assertThat(CareersBoard.fromUrl(url)).contains(new CareersBoard(platform, boardId));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://acme.example/careers",
            "https://www.linkedin.com/company/acme/jobs",
            "https://boards.greenhouse.io/",
            "https://evil.example/?u=https://jobs.lever.co/acme",
            "https://acme.wd1.myworkdayjobs.com/",
            "https://eeho.fa.us2.oraclecloud.com/hcmUI/CandidateExperience/en/sites/jobsearch",
            "not a link",
    })
    void ignoresLinksThatArentABoard(String url) {
        assertThat(CareersBoard.fromUrl(url)).isEmpty();
    }

    @Test
    void ignoresNull() {
        assertThat(CareersBoard.fromUrl(null)).isEmpty();
    }
}
