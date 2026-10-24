package dev.beacon.infrastructure.documents;

import dev.beacon.application.documents.PdfPrinter;
import dev.beacon.application.documents.PostingCaptureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Prints pages with an installed Chrome or Edge running headless, the same as
 * the browser's own Print → Save as PDF. No extra browser download needed.
 */
@Component
public class HeadlessBrowserPdfPrinter implements PdfPrinter {

    private static final Logger log = LoggerFactory.getLogger(HeadlessBrowserPdfPrinter.class);

    private static final long TIMEOUT_SECONDS = 45;
    /** Time the page's own scripts get to render before printing; job boards are often client-rendered. */
    private static final int SCRIPT_BUDGET_MS = 5_000;

    private static final List<String> KNOWN_BROWSERS = List.of(
            "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
            "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe",
            "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
            "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
            "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
            "/usr/bin/google-chrome",
            "/usr/bin/chromium",
            "/usr/bin/chromium-browser",
            "/usr/bin/microsoft-edge");

    private final String configuredBrowser;

    public HeadlessBrowserPdfPrinter(@Value("${beacon.capture.browser:}") String configuredBrowser) {
        this.configuredBrowser = configuredBrowser;
    }

    @Override
    public byte[] printUrl(URI url) {
        return print(url.toString());
    }

    @Override
    public byte[] printHtml(String html) {
        Path page = null;
        try {
            page = Files.createTempFile("beacon-print-", ".html");
            Files.writeString(page, html, StandardCharsets.UTF_8);
            return print(page.toUri().toString());
        } catch (IOException ex) {
            throw new PostingCaptureException("Could not prepare the page to print.", true);
        } finally {
            deleteQuietly(page);
        }
    }

    private byte[] print(String target) {
        var browser = findBrowser().orElseThrow(() -> new PostingCaptureException(
                "No Chrome or Edge found to print with. Install one, or set CAPTURE_BROWSER in .env to its path.", true));

        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("beacon-browser-");
            var output = workDir.resolve("page.pdf");
            var process = new ProcessBuilder(
                    browser,
                    "--headless=new",
                    "--disable-gpu",
                    "--no-first-run",
                    "--no-default-browser-check",
                    "--disable-extensions",
                    "--hide-scrollbars",
                    // A private profile, so this never attaches to a browser the user has open.
                    "--user-data-dir=" + workDir.resolve("profile"),
                    "--no-pdf-header-footer",
                    "--virtual-time-budget=" + SCRIPT_BUDGET_MS,
                    "--print-to-pdf=" + output,
                    target)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();

            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                throw new PostingCaptureException("The page took too long to load.", true);
            }

            var pdf = Files.exists(output) ? Files.readAllBytes(output) : new byte[0];
            if (pdf.length == 0) {
                throw new PostingCaptureException("The browser could not print that page.", true);
            }
            return pdf;
        } catch (IOException ex) {
            log.warn("Headless print failed", ex);
            throw new PostingCaptureException("Could not run the browser to print the page.", true);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new PostingCaptureException("Printing was interrupted.", true);
        } finally {
            deleteQuietly(workDir);
        }
    }

    private Optional<String> findBrowser() {
        if (configuredBrowser != null && !configuredBrowser.isBlank()) {
            return Optional.of(configuredBrowser.strip());
        }
        return KNOWN_BROWSERS.stream().filter(path -> Files.isExecutable(Path.of(path))).findFirst();
    }

    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try (Stream<Path> files = Files.walk(path)) {
            files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ex) {
            log.debug("Could not clean up {}", path, ex);
        }
    }
}
