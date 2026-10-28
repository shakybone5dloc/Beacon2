package dev.beacon.api.contacts;

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
class ContactsApiTests extends SignedInApiTests {

    @Test
    void logsAContactAboutAnApplicationAndPutsThePersonOnIt() {
        var appId = id(createApplication("""
                {"companyName": "Acme", "role": "Java Engineer"}
                """));
        var pat = id(createPerson("Pat", "RECRUITER"));

        var result = createContact("""
                {"personId": "%s", "applicationId": "%s", "channel": "LINKEDIN", "direction": "INBOUND",
                 "occurredAt": "2026-10-20T15:00:00Z", "body": "  Hi! I have a Java role at Acme...  "}
                """.formatted(pat, appId));

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).headers().containsHeader(HttpHeaders.LOCATION);
        assertThat(result).bodyJson().extractingPath("$.personName").isEqualTo("Pat");
        assertThat(result).bodyJson().extractingPath("$.company").isEqualTo("Acme");
        assertThat(result).bodyJson().extractingPath("$.jobRole").isEqualTo("Java Engineer");
        assertThat(result).bodyJson().extractingPath("$.body").isEqualTo("Hi! I have a Java role at Acme...");

        assertThat(mvc.get().uri("/api/applications/{id}/contacts", appId).exchange())
                .bodyJson().extractingPath("$[*].channel").asArray().containsExactly("LINKEDIN");
        // Contacting the user about an application puts them on it, in their usual role.
        var onApp = mvc.get().uri("/api/applications/{id}/people", appId).exchange();
        assertThat(onApp).bodyJson().extractingPath("$[*].person.name").asArray().containsExactly("Pat");
        assertThat(onApp).bodyJson().extractingPath("$[*].role").asArray().containsExactly("RECRUITER");
    }

    @Test
    void aPersonsLogIsNewestFirstAndCanHaveGeneralOutreach() {
        var pat = id(createPerson("Pat", "RECRUITER"));
        createContact(contact(pat, "EMAIL", "OUTBOUND", "2026-10-01T09:00:00Z"));
        createContact(contact(pat, "PHONE", "INBOUND", "2026-10-15T09:00:00Z"));

        var log = mvc.get().uri("/api/people/{id}/contacts", pat).exchange();
        assertThat(log).bodyJson().extractingPath("$[*].channel").asArray().containsExactly("PHONE", "EMAIL");
        assertThat(log).bodyJson().extractingPath("$[0].applicationId").isNull();
        assertThat(log).bodyJson().extractingPath("$[0].body").isNull();
    }

    @Test
    void rejectsMissingFieldsAndUnknownPeople() {
        var missing = createContact("""
                {"direction": "INBOUND", "occurredAt": "2026-10-01T09:00:00Z"}
                """);
        assertThat(missing).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(missing).bodyJson().extractingPath("$.errors.personId").isNotNull();
        assertThat(missing).bodyJson().extractingPath("$.errors.channel").isNotNull();

        var unknown = createContact(contact("00000000-0000-0000-0000-000000000000", "SMS", "INBOUND",
                "2026-10-01T09:00:00Z"));
        assertThat(unknown).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(unknown).bodyJson().extractingPath("$.errors.personId").isNotNull();
    }

    @Test
    void updatesWithTheVersionItWasLoadedAtAndDeletes() {
        var pat = id(createPerson("Pat", "RECRUITER"));
        var contactId = id(createContact(contact(pat, "EMAIL", "INBOUND", "2026-10-01T09:00:00Z")));

        var updated = updateContact(contactId, """
                {"personId": "%s", "channel": "PHONE", "direction": "OUTBOUND",
                 "occurredAt": "2026-10-02T09:00:00Z", "body": "Left a voicemail", "version": 0}
                """.formatted(pat));
        assertThat(updated).hasStatus(HttpStatus.OK);
        assertThat(updated).bodyJson().extractingPath("$.channel").isEqualTo("PHONE");
        assertThat(updated).bodyJson().extractingPath("$.body").isEqualTo("Left a voicemail");

        assertThat(updateContact(contactId, """
                {"personId": "%s", "channel": "SMS", "direction": "OUTBOUND",
                 "occurredAt": "2026-10-02T09:00:00Z", "version": 0}
                """.formatted(pat))).hasStatus(HttpStatus.CONFLICT);

        assertThat(mvc.delete().uri("/api/contacts/{id}", contactId).exchange()).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.delete().uri("/api/contacts/{id}", contactId).exchange()).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void deletingAnApplicationKeepsTheContactsAndDeletingThePersonRemovesThem() {
        var appId = id(createApplication("""
                {"companyName": "Acme", "role": "Engineer"}
                """));
        var pat = id(createPerson("Pat", "RECRUITER"));
        createContact("""
                {"personId": "%s", "applicationId": "%s", "channel": "EMAIL", "direction": "INBOUND",
                 "occurredAt": "2026-10-01T09:00:00Z"}
                """.formatted(pat, appId));
        mvc.get().uri("/api/applications/{id}/contacts", appId).exchange();

        assertThat(mvc.delete().uri("/api/applications/{id}", appId).exchange()).hasStatus(HttpStatus.NO_CONTENT);
        var log = mvc.get().uri("/api/people/{id}/contacts", pat).exchange();
        assertThat(log).bodyJson().extractingPath("$[*].channel").asArray().containsExactly("EMAIL");
        assertThat(log).bodyJson().extractingPath("$[0].applicationId").isNull();

        assertThat(mvc.delete().uri("/api/people/{id}", pat).exchange()).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/people/{id}/contacts", pat).exchange()).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void anotherUsersContactsAndPeopleCantBeReached() {
        var appId = id(createApplication("""
                {"companyName": "Acme", "role": "Engineer"}
                """));
        var pat = id(createPerson("Pat", "RECRUITER"));
        var contactId = id(createContact(contact(pat, "EMAIL", "INBOUND", "2026-10-01T09:00:00Z")));
        var other = as(register());

        assertThat(other.get().uri("/api/people/{id}/contacts", pat).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(other.get().uri("/api/applications/{id}/contacts", appId).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(other.delete().uri("/api/contacts/{id}", contactId).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        var theirs = other.post().uri("/api/contacts").contentType(MediaType.APPLICATION_JSON)
                .content(contact(pat, "EMAIL", "OUTBOUND", "2026-10-01T09:00:00Z")).exchange();
        assertThat(theirs).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(theirs).bodyJson().extractingPath("$.errors.personId").isNotNull();
    }

    @Test
    void flagsTheSameJobPitchedByASecondRecruiter() {
        var first = id(createApplication("""
                {"companyName": "Acme, Inc.", "role": "Sr. Java Engineer"}
                """));
        var second = id(createApplication("""
                {"companyName": "ACME", "role": "Senior Java Engineer"}
                """));
        var different = id(createApplication("""
                {"companyName": "Acme", "role": "Engineering Manager"}
                """));
        var pat = id(createPerson("Pat", "RECRUITER", "Robert Half"));
        link(first, pat);

        var duplicates = mvc.get().uri("/api/applications/{id}/possible-duplicates", second).exchange();
        assertThat(duplicates).hasStatus(HttpStatus.OK);
        assertThat(duplicates).bodyJson().extractingPath("$[*].applicationId").asArray().containsExactly(first);
        assertThat(duplicates).bodyJson().extractingPath("$[0].recruiters").asArray().containsExactly("Pat (Robert Half)");

        assertThat(mvc.get().uri("/api/applications/{id}/possible-duplicates", first).exchange())
                .bodyJson().extractingPath("$[*].applicationId").asArray().containsExactly(second);
        assertThat(mvc.get().uri("/api/applications/{id}/possible-duplicates", different).exchange())
                .bodyJson().extractingPath("$").asArray().isEmpty();
    }

    @Test
    void anUndisclosedEmployerIsNeverFlagged() {
        createApplication("""
                {"role": "Java Contractor"}
                """);
        var second = id(createApplication("""
                {"role": "Java Contractor"}
                """));

        assertThat(mvc.get().uri("/api/applications/{id}/possible-duplicates", second).exchange())
                .bodyJson().extractingPath("$").asArray().isEmpty();
        assertThat(as(register()).get().uri("/api/applications/{id}/possible-duplicates", second).exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    private static String contact(String personId, String channel, String direction, String occurredAt) {
        return """
                {"personId": "%s", "channel": "%s", "direction": "%s", "occurredAt": "%s"}
                """.formatted(personId, channel, direction, occurredAt);
    }

    private MvcTestResult createContact(String json) {
        return mvc.post().uri("/api/contacts").contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private MvcTestResult updateContact(String id, String json) {
        return mvc.put().uri("/api/contacts/{id}", id).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private MvcTestResult createPerson(String name, String role) {
        return createPerson(mvc, """
                {"name": "%s", "role": "%s"}
                """.formatted(name, role));
    }

    private MvcTestResult createPerson(String name, String role, String company) {
        return createPerson(mvc, """
                {"name": "%s", "role": "%s", "companyName": "%s"}
                """.formatted(name, role, company));
    }

    private static MvcTestResult createPerson(MockMvcTester as, String json) {
        var result = as.post().uri("/api/people").contentType(MediaType.APPLICATION_JSON).content(json).exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return result;
    }

    private MvcTestResult createApplication(String json) {
        var result = mvc.post().uri("/api/applications").contentType(MediaType.APPLICATION_JSON).content(json).exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return result;
    }

    private void link(String applicationId, String personId) {
        assertThat(mvc.put().uri("/api/applications/{a}/people/{p}", applicationId, personId)
                .contentType(MediaType.APPLICATION_JSON).content("{}").exchange()).hasStatus(HttpStatus.OK);
    }

    private static String id(MvcTestResult result) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
