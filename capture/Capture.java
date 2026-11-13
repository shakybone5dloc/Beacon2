import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Beacon's page printer, in a container of its own: headless Chromium turns a web page (or
 * HTML Beacon made) into a PDF. It runs apart from the API so that whatever a page does, it
 * can't reach the API's secrets: this container has none, sits on a network with no way out,
 * and every request Chromium makes goes through the egress proxy, which refuses private
 * addresses.
 *
 *   POST /print/url    body: the page's URL           -> application/pdf
 *   POST /print/html   body: an HTML document (UTF-8) -> application/pdf
 *   GET  /health                                      -> 200
 *
 * Failures answer 422 (the page can't be printed as asked) or 502 (the page or the browser
 * failed), with the reason as plain text for the user.
 */
public final class Capture {

    private static final int PORT = Integer.parseInt(env("CAPTURE_PORT", "8090"));
    private static final String BROWSER = env("CAPTURE_BROWSER", "/usr/bin/chromium-browser");
    /** Where every request Chromium makes goes; in the stack, the egress proxy. */
    private static final String PROXY = env("CAPTURE_PROXY", "");
    private static final long TIMEOUT_SECONDS = 45;
    /** Time the page's own scripts get to render before printing; job boards are often client-rendered. */
    private static final int SCRIPT_BUDGET_MS = 5_000;
    private static final long POLL_MS = 250;
    private static final int MAX_BODY = 5 * 1024 * 1024;
    /** Chromium is memory-hungry: a couple at once, the rest wait. */
    private static final Semaphore SLOTS = new Semaphore(Integer.parseInt(env("CAPTURE_PARALLEL", "2")));

    public static void main(String[] args) throws IOException {
        var server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/health", exchange -> send(exchange, 200, "text/plain", "ok".getBytes()));
        server.createContext("/print/url", exchange -> handle(exchange, false));
        server.createContext("/print/html", exchange -> handle(exchange, true));
        server.start();
        log("listening on " + PORT + (PROXY.isBlank() ? " WITHOUT an egress proxy" : " through " + PROXY));
    }

    private static void handle(HttpExchange exchange, boolean html) throws IOException {
        try (exchange) {
            if (!"POST".equals(exchange.getRequestMethod())) {
                send(exchange, 405, "text/plain", "POST only".getBytes());
                return;
            }
            var body = readBody(exchange.getRequestBody());
            if (body == null) {
                fail(exchange, 422, "That page is too large to print.");
                return;
            }
            var text = new String(body, StandardCharsets.UTF_8);
            try {
                byte[] pdf = html ? printHtml(text) : printUrl(text.strip());
                send(exchange, 200, "application/pdf", pdf);
            } catch (Failure failure) {
                fail(exchange, failure.status, failure.getMessage());
            }
        } catch (RuntimeException ex) {
            log("unexpected: " + ex);
        }
    }

    private static byte[] printUrl(String url) throws Failure {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException ex) {
            throw new Failure(422, "The posting URL is not a valid web address.");
        }
        var scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null) {
            throw new Failure(422, "Only http and https pages can be saved.");
        }
        return print(uri.toString());
    }

    private static byte[] printHtml(String html) throws Failure {
        Path page = null;
        try {
            page = Files.createTempFile("print-", ".html");
            Files.writeString(page, html, StandardCharsets.UTF_8);
            return print(page.toUri().toString());
        } catch (IOException ex) {
            throw new Failure(502, "Could not prepare the page to print.");
        } finally {
            deleteQuietly(page);
        }
    }

    private static byte[] print(String target) throws Failure {
        try {
            if (!SLOTS.tryAcquire(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new Failure(502, "The printer is busy. Try again in a minute.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new Failure(502, "Printing was interrupted.");
        }
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("browser-");
            var output = workDir.resolve("page.pdf");
            var browserLog = workDir.resolve("browser.log");
            var command = new ArrayList<>(List.of(
                    BROWSER,
                    "--headless=new",
                    "--disable-gpu",
                    "--no-first-run",
                    "--no-default-browser-check",
                    "--disable-extensions",
                    "--hide-scrollbars",
                    "--user-data-dir=" + workDir.resolve("profile"),
                    "--no-pdf-header-footer",
                    "--virtual-time-budget=" + SCRIPT_BUDGET_MS,
                    // The container is the sandbox: Chromium's own needs kernel features it lacks.
                    "--no-sandbox",
                    "--disable-dev-shm-usage",
                    // No UDP around the proxy (WebRTC), and none of Chromium's own calls home
                    // (component updates, safe browsing, metrics): only the page's requests.
                    "--force-webrtc-ip-handling-policy=disable_non_proxied_udp",
                    "--disable-background-networking",
                    "--disable-component-update",
                    "--disable-default-apps",
                    "--disable-client-side-phishing-detection",
                    "--disable-domain-reliability",
                    "--disable-sync",
                    "--metrics-recording-only",
                    "--no-pings",
                    "--disable-features=OptimizationHints,MediaRouter,Translate,AutofillServerCommunication",
                    "--print-to-pdf=" + output));
            if (!PROXY.isBlank()) {
                // Everything through the proxy, localhost included: "<-loopback>" removes
                // Chromium's built-in exception for it.
                command.add("--proxy-server=" + PROXY);
                command.add("--proxy-bypass-list=<-loopback>");
            }
            command.add(target);
            var process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(browserLog.toFile())
                    .start();

            var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                throw new Failure(502, "The page took too long to load.");
            }
            var pdf = waitForPdf(output, deadline);
            if (pdf.length == 0) {
                log("exit " + process.exitValue() + ", no PDF for " + describe(target) + ":\n" + tail(browserLog));
                throw new Failure(502, "The browser could not print that page.");
            }
            return pdf;
        } catch (IOException ex) {
            log("could not run the browser: " + ex);
            throw new Failure(502, "Could not run the browser to print the page.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new Failure(502, "Printing was interrupted.");
        } finally {
            deleteQuietly(workDir);
            SLOTS.release();
        }
    }

    /** Waits until the PDF exists and has stopped growing; empty if it never appears. */
    private static byte[] waitForPdf(Path output, long deadline) throws IOException, InterruptedException {
        long lastSize = -1;
        while (System.nanoTime() < deadline) {
            var size = Files.exists(output) ? Files.size(output) : 0;
            if (size > 0 && size == lastSize) return Files.readAllBytes(output);
            lastSize = size;
            Thread.sleep(POLL_MS);
        }
        return new byte[0];
    }

    /** The request body, or null when it's over the limit. */
    private static byte[] readBody(InputStream in) throws IOException {
        var bytes = in.readNBytes(MAX_BODY + 1);
        return bytes.length > MAX_BODY ? null : bytes;
    }

    private static void fail(HttpExchange exchange, int status, String message) throws IOException {
        send(exchange, status, "text/plain; charset=utf-8", message.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    /** Pages printed from HTML are temporary files; only their kind is worth logging. */
    private static String describe(String target) {
        return target.startsWith("file:") ? "generated HTML" : target;
    }

    private static String tail(Path browserLog) {
        try {
            var lines = Files.readAllLines(browserLog, StandardCharsets.UTF_8);
            return String.join("\n", lines.subList(Math.max(0, lines.size() - 20), lines.size()));
        } catch (IOException ex) {
            return "(no output)";
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try (Stream<Path> files = Files.walk(path)) {
            files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // A temp file left behind is cleaned up when the container restarts.
        }
    }

    private static String env(String name, String fallback) {
        var value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static void log(String message) {
        System.out.println("[capture] " + message);
    }

    private static final class Failure extends Exception {
        final int status;

        Failure(int status, String message) {
            super(message);
            this.status = status;
        }
    }
}
