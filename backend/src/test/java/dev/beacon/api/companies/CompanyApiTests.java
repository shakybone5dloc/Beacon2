package dev.beacon.api.companies;

import com.jayway.jsonpath.JsonPath;
import dev.beacon.SignedInApiTests;
import dev.beacon.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.UnsupportedEncodingException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class CompanyApiTests extends SignedInApiTests {

    @Test
    void namingACompanyAgainLinksTheSameOneEvenWrittenDifferently() {
        var first = createApplication("""
                {"companyName": "Acme, Inc.", "role": "Engineer"}
                """);
        var second = createApplication("""
                {"companyName": "  ACME ", "role": "Architect"}
                """);

        assertThat(read(second, "$.companyId")).isEqualTo(read(first, "$.companyId"));
        assertThat(read(second, "$.company")).isEqualTo("Acme, Inc.");

        var companies = mvc.get().uri("/api/companies").exchange();
        assertThat(companies).bodyJson().extractingPath("$[*].name").asArray().containsExactly("Acme, Inc.");
        assertThat(companies).bodyJson().extractingPath("$[0].applicationCount").isEqualTo(2);
        assertThat(companies).bodyJson().extractingPath("$[0].type").isEqualTo("EMPLOYER");
    }

    @Test
    void anExistingCompanyCanBeLinkedById() {
        var companyId = read(createApplication("""
                {"companyName": "Globex", "role": "Engineer"}
                """), "$.companyId");

        var linked = createApplication("""
                {"companyId": "%s", "companyName": "ignored when an id is given", "role": "Tester"}
                """.formatted(companyId));

        assertThat(read(linked, "$.companyId")).isEqualTo(companyId);
        assertThat(read(linked, "$.company")).isEqualTo("Globex");
    }

    @Test
    void searchFindsCompaniesByPartOfTheirName() {
        for (var name : new String[] {"Initech", "Initrode", "Hooli"}) {
            createApplication("""
                    {"companyName": "%s", "role": "Engineer"}
                    """.formatted(name));
        }

        assertThat(mvc.get().uri("/api/companies?q=INIT").exchange())
                .bodyJson().extractingPath("$[*].name").asArray().containsExactly("Initech", "Initrode");
        assertThat(mvc.get().uri("/api/companies").exchange())
                .bodyJson().extractingPath("$[*].name").asArray().containsExactly("Hooli", "Initech", "Initrode");
    }

    @Test
    void aCompanyListsItsApplications() {
        var companyId = read(createApplication("""
                {"companyName": "Acme", "role": "Engineer"}
                """), "$.companyId");
        createApplication("""
                {"companyName": "Other", "role": "Engineer"}
                """);

        var apps = mvc.get().uri("/api/companies/{id}/applications", companyId).exchange();
        assertThat(apps).bodyJson().extractingPath("$[*].role").asArray().containsExactly("Engineer");
        assertThat(apps).bodyJson().extractingPath("$[0].company").isEqualTo("Acme");
    }

    @Test
    void editingACompanyKeepsItsLinksAndCleansUpDomains() {
        var app = createApplication("""
                {"companyName": "Acme", "role": "Engineer"}
                """);
        var companyId = read(app, "$.companyId");

        var updated = update(companyId, """
                {"name": "Acme Staffing", "type": "AGENCY", "website": "https://acme.example",
                 "emailDomains": [" ACME.example ", "acme.example", "acmejobs.example"], "notes": "Recruiter Pat", "version": 0}
                """);

        assertThat(updated).hasStatus(HttpStatus.OK);
        assertThat(updated).bodyJson().extractingPath("$.type").isEqualTo("AGENCY");
        assertThat(updated).bodyJson().extractingPath("$.emailDomains").asArray()
                .containsExactly("acme.example", "acmejobs.example");
        assertThat(mvc.get().uri("/api/applications/{id}", read(app, "$.id")).exchange())
                .bodyJson().extractingPath("$.company").isEqualTo("Acme Staffing");
    }

    @Test
    void editingRejectsATakenNameAStaleVersionAndABadDomain() {
        createApplication("""
                {"companyName": "Globex", "role": "Engineer"}
                """);
        var acmeId = read(createApplication("""
                {"companyName": "Acme", "role": "Engineer"}
                """), "$.companyId");

        var taken = update(acmeId, """
                {"name": "GLOBEX", "type": "EMPLOYER", "version": 0}
                """);
        assertThat(taken).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(taken).bodyJson().extractingPath("$.errors.name").isNotNull();

        assertThat(update(acmeId, """
                {"name": "Acme", "type": "EMPLOYER", "version": 7}
                """)).hasStatus(HttpStatus.CONFLICT);

        assertThat(update(acmeId, """
                {"name": "Acme", "type": "EMPLOYER", "emailDomains": ["not a domain"], "version": 0}
                """)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownCompanyIs404() {
        assertThat(mvc.get().uri("/api/companies/{id}", "00000000-0000-0000-0000-000000000000").exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    private MvcTestResult createApplication(String json) {
        var result = mvc.post().uri("/api/applications").contentType(MediaType.APPLICATION_JSON).content(json).exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return result;
    }

    private MvcTestResult update(String companyId, String json) {
        return mvc.put().uri("/api/companies/{id}", companyId).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private static String read(MvcTestResult result, String path) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), path);
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
