package dev.beacon.domain.net;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * Which addresses the server must never be sent to on a user's behalf: anything that isn't
 * the public internet. Fetching a user-given URL that resolves to one of these could reach
 * the database, the home network or tailnet, or a cloud server's metadata service, which
 * hands out its credentials.
 */
public final class NetworkAddresses {

    private NetworkAddresses() {}

    public static boolean isPrivate(InetAddress address) {
        if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                || address.isAnyLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        var b = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = b[0] & 0xff;
            int second = b[1] & 0xff;
            return first == 0                                     // "this network"
                    || (first == 100 && second >= 64 && second <= 127) // 100.64.0.0/10: carrier NAT, Tailscale
                    || (first == 192 && second == 0 && (b[2] & 0xff) == 0) // 192.0.0.0/24: protocol assignments
                    || (first == 198 && (second == 18 || second == 19))    // 198.18.0.0/15: benchmarking
                    || first >= 240;                              // 240.0.0.0/4: reserved, and broadcast
        }
        if (address instanceof Inet6Address) {
            // fc00::/7: unique local addresses, the IPv6 private ranges (Java's site-local is the old fec0::/10).
            if ((b[0] & 0xfe) == 0xfc) return true;
            // ::ffff:a.b.c.d is an IPv4 address in IPv6 clothing: judge the IPv4 address.
            if (isIpv4Mapped(b)) {
                try {
                    return isPrivate(InetAddress.getByAddress(new byte[] {b[12], b[13], b[14], b[15]}));
                } catch (java.net.UnknownHostException ex) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isIpv4Mapped(byte[] b) {
        for (int i = 0; i < 10; i++) if (b[i] != 0) return false;
        return (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff;
    }
}
