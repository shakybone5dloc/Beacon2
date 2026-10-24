package dev.beacon.application.documents;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * The server opens job URLs in a browser, so they must be ordinary public web
 * pages: never localhost or a private-network address.
 */
final class PublicUrls {

    private PublicUrls() {}

    static URI require(String url) {
        if (url == null || url.isBlank()) {
            throw new PostingCaptureException("Add the posting's URL to this application first.", false);
        }

        URI uri;
        try {
            uri = URI.create(url.strip());
        } catch (IllegalArgumentException ex) {
            throw new PostingCaptureException("The posting URL is not a valid web address.", false);
        }

        var scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https") || uri.getHost() == null) {
            throw new PostingCaptureException("Only http and https pages can be saved.", false);
        }

        try {
            for (var address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                        || address.isAnyLocalAddress() || address.isMulticastAddress()) {
                    throw new PostingCaptureException("That URL points at a private network address.", false);
                }
            }
        } catch (UnknownHostException ex) {
            throw new PostingCaptureException("Could not find the posting's website (" + uri.getHost() + ").", true);
        }
        return uri;
    }
}
