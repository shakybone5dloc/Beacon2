package dev.beacon.api;

import dev.beacon.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiSpecTests {

    @Autowired MockMvcTester mvc;

    private MvcTestResult spec() {
        return mvc.get().uri("/v3/api-docs").exchange();   // no sign-in: the docs are public
    }

    @Test
    void specIsPublic() {
        assertThat(spec()).hasStatus(HttpStatus.OK);
    }

    @Test
    void everyOperationHasAStableUniqueId() {
        var spec = spec();

        assertThat(spec).bodyJson()
                .extractingPath("$.paths['/api/applications'].get.operationId")
                .isEqualTo("listApplications");

        assertThat(spec).bodyJson()
                .extractingPath("$..operationId").asArray()   // ".." = search the whole document
                .doesNotHaveDuplicates()
                .noneMatch(id -> id.toString().matches(".*_\\d+$"));   // no list_1, delete_3...
    }

    @Test
    void responsesDescribeStatusAsAnEnumAndPropertiesAsRequired() {
        var spec = spec();

        assertThat(spec).bodyJson()
                .extractingPath("$.components.schemas.ApplicationResponse.required").asArray()
                .contains("id", "company", "status", "version");
        assertThat(spec).bodyJson()
                .extractingPath("$.components.schemas.ApplicationResponse.properties.status.enum").asArray()
                .contains("SAVED", "INTERVIEWING");
        assertThat(spec).bodyJson()
                .extractingPath("$.components.schemas.ApplicationResponse.properties.allowedTransitions.items.enum").asArray()
                .contains("APPLIED");
        assertThat(spec).bodyJson()
                .extractingPath("$.components.schemas.ApplicationResponse.properties.jobDescription").asMap()
                .doesNotContainKey("enum");
    }

    /** Every handler takes @AuthenticationPrincipal SignedInUser; it comes from the session, never from the client. */
    @Test
    void theSignedInUserIsNeverAParameter() {
        assertThat(spec()).bodyJson()
                .extractingPath("$..parameters[?(@.name == 'user')]").asArray()
                .isEmpty();
    }

    @Test
    void nullableFieldsAllowNull() {
        var spec = spec();

        assertThat(spec).bodyJson()
                .extractingPath("$.components.schemas.ApplicationResponse.properties.jobDescription.type").asArray()
                .containsExactlyInAnyOrder("string", "null");
        assertThat(spec).bodyJson()
                .extractingPath("$.components.schemas.ApplicationResponse.properties.company.type")
                .isEqualTo("string");
    }

    @Test
    void createdAndDeletedUseTheirRealStatusCodes() {
        var spec = spec();

        assertThat(spec).bodyJson()
                .extractingPath("$.paths['/api/applications'].post.responses['201'].content").isNotNull();
        assertThat(spec).bodyJson()
                .extractingPath("$.paths['/api/applications/{id}'].delete.responses").asMap()
                .containsKey("204")
                .doesNotContainKey("200");
    }

    /**
     * The Angular client is generated from frontend/openapi.json, so it must match the API.
     * After changing the API, refresh it with:
     * {@code ./mvnw test -Dtest=OpenApiSpecTests -Dopenapi.update=true}, then {@code npm run generate:api}.
     */
    @Test
    void frontendCopyOfTheSpecIsUpToDate() throws Exception {
        var mapper = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
        var live = mapper.readTree(spec().getResponse().getContentAsByteArray());
        var committed = Path.of("..", "frontend", "openapi.json");

        if (Boolean.getBoolean("openapi.update")) {
            Files.writeString(committed, mapper.writeValueAsString(live) + "\n", StandardCharsets.UTF_8);
        }

        assertThat(Files.exists(committed))
                .as("frontend/openapi.json is missing; run with -Dopenapi.update=true").isTrue();
        assertThat(mapper.readTree(Files.readString(committed, StandardCharsets.UTF_8)))
                .as("frontend/openapi.json is out of date; run with -Dopenapi.update=true")
                .isEqualTo(live);
    }
}