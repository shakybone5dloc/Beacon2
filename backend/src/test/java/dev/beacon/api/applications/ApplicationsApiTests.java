package dev.beacon.api.applications;

import dev.beacon.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ApplicationsApiTests {

    @Autowired MockMvcTester mvc;

    @Test
    void createReturns201WithLocationAndBody() {
        var result = mvc.post().uri("/api/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"company": "  Acme  ", "role": "Senior Engineer"}
                        """)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).headers().containsHeader(HttpHeaders.LOCATION);
        assertThat(result).bodyJson().extractingPath("$.company").isEqualTo("Acme");
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("SAVED");
    }

    @Test
    void createWithBlankCompanyReturns400WithFieldErrors() {
        var result = mvc.post().uri("/api/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"company": "", "role": "Senior Engineer"}
                """)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errors.company").isNotNull();
    }

    @Test
    void getUnknownIdReturns404() {
        var result = mvc.get(). uri("/api/applications/{id}", UUID.randomUUID()).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void createdApplicationCanBeFetchedByLocation() {
        var result = mvc.post().uri("/api/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"company": "  Acme  ", "role": "Senior Engineer"}
                        """)
                .exchange();

        var location = result.getResponse().getHeader(HttpHeaders.LOCATION);

        var fetched = mvc.get().uri(location).exchange();

        assertThat(fetched).hasStatus(HttpStatus.OK);
        assertThat(fetched).bodyJson().extractingPath("$.company").isEqualTo("Acme");
    }

    @Test
    void canGetNewestApplicationFirst() {
        create("A");
        create("B");

        var result = mvc.get().uri("/api/applications").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$[0].company").isEqualTo("B");
        assertThat(result).bodyJson().extractingPath("$[1].company").isEqualTo("A");
    }

    private void create(String company) {
        var result = mvc.post().uri("/api/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"company": "%s", "role": "Senior Engineer"}
                        """.formatted(company))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
    }
}
