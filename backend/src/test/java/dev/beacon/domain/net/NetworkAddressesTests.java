package dev.beacon.domain.net;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;

class NetworkAddressesTests {

    @ParameterizedTest
    @ValueSource(strings = {
            "127.0.0.1", "10.1.2.3", "172.16.0.1", "172.31.255.255", "192.168.1.10",
            "169.254.169.254",              // cloud metadata service
            "100.64.0.1", "100.101.102.103", "100.127.255.254", // Tailscale and carrier NAT
            "0.0.0.0", "0.1.2.3", "192.0.0.8", "198.18.0.1", "198.19.255.1", "240.0.0.1", "255.255.255.255",
            "224.0.0.1",
            "::1", "::", "fe80::1", "fc00::1", "fd7a:115c:a1e0::1", // fd7a:115c:a1e0::/48 is Tailscale's
            "::ffff:127.0.0.1", "::ffff:169.254.169.254", "::ffff:10.0.0.1"})
    void privateAddressesAreRefused(String address) throws UnknownHostException {
        assertThat(NetworkAddresses.isPrivate(InetAddress.getByName(address))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "93.184.215.14", "8.8.8.8", "100.63.255.255", "100.128.0.1", "172.32.0.1", "192.0.1.1",
            "198.20.0.1", "2606:4700::6810:84e5", "::ffff:8.8.8.8"})
    void publicAddressesAreAllowed(String address) throws UnknownHostException {
        assertThat(NetworkAddresses.isPrivate(InetAddress.getByName(address))).isFalse();
    }
}
