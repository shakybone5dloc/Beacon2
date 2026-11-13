package dev.beacon.infrastructure.postings;

import dev.beacon.domain.companies.CareersBoard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import dev.beacon.domain.net.NetworkAddresses;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Works out the careers board behind a link. A board link (jobs.lever.co/acme) is read
 * as it is; a company's own careers page (careers.oracle.com) is opened once and
 * searched for the board it embeds or loads from. Only public web pages are opened.
 */
@Component
public class CareersPageFinder {

    private static final Logger log = LoggerFactory.getLogger(CareersPageFinder.class);

    private static final int MAX_BYTES = 2_000_000;
    private static final int MAX_REDIRECTS = 3;

    private static final Pattern LINK = Pattern.compile("https?://[^\\s\"'<>()\\\\]+");
    private static final Pattern ORACLE_HOST = Pattern.compile("https://([A-Za-z0-9-]+\\.fa(?:\\.[A-Za-z0-9-]+)*)\\.oraclecloud\\.com");
    private static final Pattern SITE_NUMBER = Pattern.compile("siteNumber=(CX_\\d+)");

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)   // followed by hand, checking each hop is public
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** Empty when the link isn't a board and the page doesn't lead to one Beacon can read. */
    public Optional<CareersBoard> find(String url) {
        var direct = CareersBoard.fromUrl(url);
        if (direct.isPresent() || url == null) return direct;
        try {
            return scan(fetch(URI.create(url.strip()), MAX_REDIRECTS));
        } catch (IOException | RuntimeException ex) {
            log.debug("Couldn't find a careers board at {}", url, ex);
            return Optional.empty();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /** The first known board the page links to, else an Oracle site the page loads from. */
    static Optional<CareersBoard> scan(String html) {
        var page = html.replace("\\/", "/").replace("&amp;", "&");
        var links = LINK.matcher(page);
        while (links.find()) {
            var board = CareersBoard.fromUrl(links.group());
            if (board.isPresent()) return board;
        }
        var host = ORACLE_HOST.matcher(page);
        var site = SITE_NUMBER.matcher(page);
        if (host.find() && site.find()) return Optional.of(CareersBoard.oracle(host.group(1), site.group(1)));
        return Optional.empty();
    }

    private String fetch(URI uri, int redirectsLeft) throws IOException, InterruptedException {
        requirePublic(uri);
        var request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "Mozilla/5.0 (compatible; Beacon job tracker)")
                .header("Accept", "text/html")
                .GET()
                .build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (var body = response.body()) {
            var status = response.statusCode();
            if (status >= 300 && status < 400 && redirectsLeft > 0) {
                var location = response.headers().firstValue("Location")
                        .orElseThrow(() -> new IOException("Redirect without a location"));
                return fetch(uri.resolve(location), redirectsLeft - 1);
            }
            if (status != 200) throw new IOException("HTTP " + status);
            return new String(body.readNBytes(MAX_BYTES), StandardCharsets.UTF_8);
        }
    }

    /** Never localhost or a private-network address. */
    private static void requirePublic(URI uri) throws IOException {
        var scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https") || uri.getHost() == null) {
            throw new IOException("Not a web page: " + uri);
        }
        for (var address : InetAddress.getAllByName(uri.getHost())) {
            if (NetworkAddresses.isPrivate(address)) {
                throw new IOException("Private network address: " + uri.getHost());
            }
        }
    }
}
