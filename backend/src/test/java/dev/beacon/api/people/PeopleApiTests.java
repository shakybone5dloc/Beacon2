package dev.beacon.api.people;

import com.jayway.jsonpath.JsonPath;
import dev.beacon.SignedInApiTests;
import dev.beacon.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.UnsupportedEncodingException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class PeopleApiTests extends SignedInApiTests {

    @Test
    void createsAPersonAtACompanyByName() {
        var result = createPerson("""
                {"name": "  Pat Lee ", "companyName": "Robert Half", "title": "Senior Recruiter",
                 "role": "RECRUITER", "recruiterType": "AGENCY", "email": "pat@roberthalf.example",
                 "linkedinUrl": "https://www.linkedin.com/in/patlee"}
                """);

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).headers().containsHeader(HttpHeaders.LOCATION);
        assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("Pat Lee");
        assertThat(result).bodyJson().extractingPath("$.company").isEqualTo("Robert Half");
        assertThat(result).bodyJson().extractingPath("$.recruiterType").isEqualTo("AGENCY");
        var companies = mvc.get().uri("/api/companies").exchange();
        assertThat(companies).bodyJson().extractingPath("$[*].name").asArray().containsExactly("Robert Half");
        // Named first by an agency recruiter, so it starts out as an agency.
        assertThat(companies).bodyJson().extractingPath("$[0].type").isEqualTo("AGENCY");
    }

    @Test
    void aRecruiterTypeOnlyStaysOnRecruiters() {
        var result = createPerson("""
                {"name": "Sam", "role": "HIRING_MANAGER", "recruiterType": "AGENCY"}
                """);

        assertThat(result).bodyJson().extractingPath("$.recruiterType").isNull();
        assertThat(result).bodyJson().extractingPath("$.company").isNull();
    }

    @Test
    void rejectsABadEmailAndAMissingRole() {
        var badEmail = createPerson("""
                {"name": "Sam", "role": "OTHER", "email": "not an email"}
                """);
        assertThat(badEmail).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(badEmail).bodyJson().extractingPath("$.errors.email").isNotNull();

        var noRole = createPerson("""
                {"name": "Sam"}
                """);
        assertThat(noRole).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(noRole).bodyJson().extractingPath("$.errors.role").isNotNull();
    }

    @Test
    void listsAndSearchesByName() {
        for (var name : new String[] {"Pat Lee", "Patricia Moss", "Sam Ortiz"}) {
            createPerson("""
                    {"name": "%s", "role": "OTHER"}
                    """.formatted(name));
        }

        assertThat(mvc.get().uri("/api/people?q=pat").exchange())
                .bodyJson().extractingPath("$[*].name").asArray().containsExactly("Pat Lee", "Patricia Moss");
        assertThat(mvc.get().uri("/api/people").exchange())
                .bodyJson().extractingPath("$[*].name").asArray().containsExactly("Pat Lee", "Patricia Moss", "Sam Ortiz");
    }

    @Test
    void updatesWithTheVersionItWasLoadedAt() {
        var id = id(createPerson("""
                {"name": "Pat", "role": "OTHER"}
                """));

        var updated = updatePerson(id, """
                {"name": "Pat Lee", "role": "INTERVIEWER", "title": "Staff Engineer", "version": 0}
                """);
        assertThat(updated).hasStatus(HttpStatus.OK);
        assertThat(updated).bodyJson().extractingPath("$.role").isEqualTo("INTERVIEWER");

        assertThat(updatePerson(id, """
                {"name": "Pat", "role": "OTHER", "version": 0}
                """)).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void linksPeopleToAnApplicationWithARolePerApplication() {
        var appId = id(createApplication("""
                {"companyName": "Acme", "role": "Java Engineer"}
                """));
        var pat = id(createPerson("""
                {"name": "Pat", "role": "RECRUITER"}
                """));
        var sam = id(createPerson("""
                {"name": "Sam", "role": "OTHER"}
                """));

        assertThat(link(appId, pat, "{}")).bodyJson().extractingPath("$.role").isEqualTo("RECRUITER");
        assertThat(link(appId, sam, """
                {"role": "REFERRAL"}
                """)).bodyJson().extractingPath("$.role").isEqualTo("REFERRAL");
        // Linking again changes the role rather than adding them twice.
        link(appId, sam, """
                {"role": "INTERVIEWER"}
                """);

        var onApp = mvc.get().uri("/api/applications/{id}/people", appId).exchange();
        assertThat(onApp).bodyJson().extractingPath("$[*].person.name").asArray().containsExactly("Pat", "Sam");
        assertThat(onApp).bodyJson().extractingPath("$[*].role").asArray().containsExactly("RECRUITER", "INTERVIEWER");

        var samsApps = mvc.get().uri("/api/people/{id}/applications", sam).exchange();
        assertThat(samsApps).bodyJson().extractingPath("$[0].company").isEqualTo("Acme");
        assertThat(samsApps).bodyJson().extractingPath("$[0].jobRole").isEqualTo("Java Engineer");
        assertThat(samsApps).bodyJson().extractingPath("$[0].role").isEqualTo("INTERVIEWER");

        assertThat(mvc.delete().uri("/api/applications/{a}/people/{p}", appId, sam).exchange())
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.delete().uri("/api/applications/{a}/people/{p}", appId, sam).exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/api/applications/{id}/people", appId).exchange())
                .bodyJson().extractingPath("$[*].person.name").asArray().containsExactly("Pat");
    }

    @Test
    void deletingAPersonTakesThemOffTheirApplications() {
        var appId = id(createApplication("""
                {"companyName": "Acme", "role": "Engineer"}
                """));
        var pat = id(createPerson("""
                {"name": "Pat", "role": "RECRUITER"}
                """));
        link(appId, pat, "{}");

        assertThat(mvc.delete().uri("/api/people/{id}", pat).exchange()).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/applications/{id}/people", appId).exchange())
                .bodyJson().extractingPath("$").asArray().isEmpty();
        assertThat(mvc.get().uri("/api/applications/{id}", appId).exchange()).hasStatus(HttpStatus.OK);
    }

    @Test
    void deletingAnApplicationKeepsItsPeople() {
        var appId = id(createApplication("""
                {"companyName": "Acme", "role": "Engineer"}
                """));
        var pat = id(createPerson("""
                {"name": "Pat", "role": "RECRUITER"}
                """));
        link(appId, pat, "{}");
        mvc.get().uri("/api/applications/{id}/people", appId).exchange();

        assertThat(mvc.delete().uri("/api/applications/{id}", appId).exchange()).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/people/{id}", pat).exchange()).hasStatus(HttpStatus.OK);
        assertThat(mvc.get().uri("/api/people/{id}/applications", pat).exchange())
                .bodyJson().extractingPath("$").asArray().isEmpty();
    }

    @Test
    void anUndisclosedRoleShowsTheRecruitersAgency() {
        var undisclosed = id(createApplication("""
                {"role": "Java Contractor", "employmentType": "C2C"}
                """));
        var disclosed = id(createApplication("""
                {"companyName": "Acme", "role": "Engineer"}
                """));
        var pat = id(createPerson("""
                {"name": "Pat", "companyName": "Robert Half", "role": "RECRUITER", "recruiterType": "AGENCY"}
                """));
        link(undisclosed, pat, "{}");
        link(disclosed, pat, "{}");

        var board = mvc.get().uri("/api/applications").exchange();
        assertThat(board).bodyJson().extractingPath("$[?(@.role == 'Java Contractor')].viaAgency").asArray()
                .containsExactly("Robert Half");
        assertThat(board).bodyJson().extractingPath("$[?(@.role == 'Engineer')].viaAgency").asArray()
                .containsExactly((Object) null);

        var one = mvc.get().uri("/api/applications/{id}", undisclosed).exchange();
        assertThat(one).bodyJson().extractingPath("$.viaAgency").isEqualTo("Robert Half");
        assertThat(one).bodyJson().extractingPath("$.employmentType").isEqualTo("C2C");
    }

    @Test
    void aCompanyListsItsPeople() {
        var pat = createPerson("""
                {"name": "Pat", "companyName": "Robert Half", "role": "RECRUITER"}
                """);
        createPerson("""
                {"name": "Sam", "companyName": "Elsewhere", "role": "OTHER"}
                """);

        assertThat(mvc.get().uri("/api/companies/{id}/people", read(pat, "$.companyId")).exchange())
                .bodyJson().extractingPath("$[*].name").asArray().containsExactly("Pat");
    }

    @Test
    void anotherUsersPeopleAndApplicationsCantBeReachedOrLinked() {
        var myApp = id(createApplication("""
                {"companyName": "Acme", "role": "Engineer"}
                """));
        var me = createPerson("""
                {"name": "Pat", "companyName": "Robert Half", "role": "RECRUITER"}
                """);
        var myPerson = id(me);
        var other = as(register());
        var theirApp = id(createApplication(other, """
                {"companyName": "Globex", "role": "Engineer"}
                """));

        assertThat(other.get().uri("/api/people").exchange()).bodyJson().extractingPath("$").asArray().isEmpty();
        assertThat(other.get().uri("/api/people/{id}", myPerson).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(other.get().uri("/api/applications/{id}/people", myApp).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(other.put().uri("/api/applications/{a}/people/{p}", theirApp, myPerson)
                .contentType(MediaType.APPLICATION_JSON).content("{}").exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(other.delete().uri("/api/people/{id}", myPerson).exchange()).hasStatus(HttpStatus.NOT_FOUND);

        var stolenCompany = other.post().uri("/api/people")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Sam", "companyId": "%s", "role": "OTHER"}
                        """.formatted(read(me, "$.companyId")))
                .exchange();
        assertThat(stolenCompany).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(stolenCompany).bodyJson().extractingPath("$.errors.companyId").isNotNull();
    }

    private MvcTestResult createPerson(String json) {
        return mvc.post().uri("/api/people").contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private MvcTestResult updatePerson(String id, String json) {
        return mvc.put().uri("/api/people/{id}", id).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private MvcTestResult createApplication(String json) {
        return createApplication(mvc, json);
    }

    private static MvcTestResult createApplication(MockMvcTester as, String json) {
        var result = as.post().uri("/api/applications").contentType(MediaType.APPLICATION_JSON).content(json).exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return result;
    }

    private MvcTestResult link(String applicationId, String personId, String json) {
        var result = mvc.put().uri("/api/applications/{a}/people/{p}", applicationId, personId)
                .contentType(MediaType.APPLICATION_JSON).content(json).exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        return result;
    }

    private static String id(MvcTestResult result) {
        return read(result, "$.id");
    }

    private static String read(MvcTestResult result, String path) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), path);
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
