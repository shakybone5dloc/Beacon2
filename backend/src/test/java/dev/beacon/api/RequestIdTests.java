package dev.beacon.api;

import dev.beacon.SignedInApiTests;
import dev.beacon.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/** Every response carries X-Request-Id, and error bodies repeat it. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class RequestIdTests extends SignedInApiTests {

    @Autowired WebApplicationContext webContext;

    @Test
    void generatesAnIdWhenNoneIsSent() {
        var result = mvc.get().uri("/api/applications").exchange();
        assertThat(result.getResponse().getHeader("X-Request-Id")).isNotBlank();
    }

    @Test
    void echoesTheCallersId() {
        var result = mvc.get().uri("/api/applications").header("X-Request-Id", "abc-123").exchange();
        assertThat(result.getResponse().getHeader("X-Request-Id")).isEqualTo("abc-123");
    }

    @Test
    void replacesAnUnsafeId() {
        var result = mvc.get().uri("/api/applications").header("X-Request-Id", "bad id\"{}").exchange();
        assertThat(result.getResponse().getHeader("X-Request-Id")).isNotEqualTo("bad id\"{}").isNotBlank();
    }

    @Test
    void errorBodiesIncludeTheId() {
        var result = mvc.post().uri("/api/applications")
                .header("X-Request-Id", "req-42")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .exchange();
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result).bodyJson().extractingPath("$.requestId").isEqualTo("req-42");
    }

    @Test
    void unauthenticatedResponsesCarryTheHeaderToo() {
        var anonymous = MockMvcTester.create(MockMvcBuilders.webAppContextSetup(webContext).apply(springSecurity()).build());
        var result = anonymous.get().uri("/api/applications").exchange();
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getHeader("X-Request-Id")).isNotBlank();
    }
}
