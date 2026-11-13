package dev.beacon.infrastructure.documents;

import com.sun.net.httpserver.HttpServer;
import dev.beacon.application.documents.PostingCaptureException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Against a stand-in for the capture service, on a local port. */
class RemotePdfPrinterTests {

    private HttpServer server;
    private final List<String> received = new ArrayList<>();
    private int status = 200;
    private byte[] reply = "%PDF-1.7 fake".getBytes(StandardCharsets.US_ASCII);

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/print/", exchange -> {
            try (exchange) {
                received.add(exchange.getRequestURI().getPath() + " " + new String(
                        exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                exchange.sendResponseHeaders(status, reply.length);
                exchange.getResponseBody().write(reply);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private RemotePdfPrinter printer() {
        return new RemotePdfPrinter("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @Test
    void sendsTheUrlOrTheHtmlAndReturnsThePdf() {
        assertThat(printer().printUrl(URI.create("https://jobs.example/1"))).startsWith("%PDF".getBytes());
        assertThat(printer().printHtml("<h1>Cover letter</h1>")).startsWith("%PDF".getBytes());

        assertThat(received).containsExactly(
                "/print/url https://jobs.example/1",
                "/print/html <h1>Cover letter</h1>");
    }

    @Test
    void passesOnTheServicesReasonForAPageThatCantBePrinted() {
        status = 422;
        reply = "Only http and https pages can be saved.".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> printer().printUrl(URI.create("https://jobs.example/1")))
                .isInstanceOf(PostingCaptureException.class)
                .hasMessage("Only http and https pages can be saved.")
                .matches(ex -> !((PostingCaptureException) ex).isUpstream());
    }

    @Test
    void aFailedPageIsAnUpstreamFailure() {
        status = 502;
        reply = "The page took too long to load.".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> printer().printUrl(URI.create("https://jobs.example/1")))
                .isInstanceOf(PostingCaptureException.class)
                .hasMessage("The page took too long to load.")
                .matches(ex -> ((PostingCaptureException) ex).isUpstream());
    }

    @Test
    void saysSoWhenTheServiceIsDown() {
        var port = server.getAddress().getPort();
        server.stop(0);

        assertThatThrownBy(() -> new RemotePdfPrinter("http://127.0.0.1:" + port).printUrl(URI.create("https://x.example")))
                .isInstanceOf(PostingCaptureException.class)
                .hasMessageContaining("isn't available right now");
    }
}
