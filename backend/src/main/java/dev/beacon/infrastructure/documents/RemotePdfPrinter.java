package dev.beacon.infrastructure.documents;

import dev.beacon.application.documents.PdfPrinter;
import dev.beacon.application.documents.PostingCaptureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Prints pages with Beacon's capture service (the {@code capture/} image): headless Chromium
 * in a container of its own, with no secrets and no way out except a proxy that refuses
 * private addresses. Used whenever {@code beacon.capture.url} is set, as it is in the deployed
 * stack; without it, {@link HeadlessBrowserPdfPrinter} prints with a browser on this machine.
 */
@Component
@ConditionalOnExpression("'${beacon.capture.url:}' != ''")
public class RemotePdfPrinter implements PdfPrinter {

    private static final Logger log = LoggerFactory.getLogger(RemotePdfPrinter.class);

    /** The service gives a page 45 seconds, and may queue it behind another. */
    private static final Duration TIMEOUT = Duration.ofSeconds(100);

    private final URI base;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public RemotePdfPrinter(@Value("${beacon.capture.url}") String url) {
        this.base = URI.create(url.endsWith("/") ? url : url + "/");
    }

    @Override
    public byte[] printUrl(URI url) {
        return print("print/url", url.toString());
    }

    @Override
    public byte[] printHtml(String html) {
        return print("print/html", html);
    }

    private byte[] print(String path, String body) {
        var request = HttpRequest.newBuilder(base.resolve(path))
                .timeout(TIMEOUT)
                .header("Content-Type", "text/plain; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<byte[]> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException ex) {
            log.warn("Capture service at {} unreachable: {}", base, ex.toString());
            throw new PostingCaptureException("The page printer isn't available right now. Try again in a minute.", true);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new PostingCaptureException("Printing was interrupted.", true);
        }
        var status = response.statusCode();
        if (status == 200) return response.body();
        // The service answers with a sentence meant for the user.
        var message = new String(response.body(), StandardCharsets.UTF_8).strip();
        if (message.isEmpty() || message.length() > 300) message = "The page could not be printed.";
        // 422: the page can't be printed as asked; anything else: the page or the printer failed.
        throw new PostingCaptureException(message, status != 422);
    }
}
