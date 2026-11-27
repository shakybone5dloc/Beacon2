package dev.beacon.config;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestIdFilterTests {

    @Test
    void putsTheIdInTheMdcDuringTheRequestAndClearsItAfter() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "req-1");
        var response = new MockHttpServletResponse();
        var seen = new String[1];

        new RequestIdFilter().doFilter(request, response, (req, res) -> seen[0] = MDC.get("requestId"));

        assertThat(seen[0]).isEqualTo("req-1");
        assertThat(response.getHeader("X-Request-Id")).isEqualTo("req-1");
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void clearsTheMdcWhenTheRequestFails() {
        assertThatThrownBy(() -> new RequestIdFilter().doFilter(
                new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> {
                    throw new IOException("boom");
                })).isInstanceOf(IOException.class);

        assertThat(MDC.get("requestId")).isNull();
    }
}
