package com.microsocks;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

public class Socks5Address {
    public final static short SOCKS5_ADDRESS_IPV4 = 1;
    public final static short SOCKS5_ADDRESS_IPV6 = 4;
    public final static short SOCKS5_ADDRESS_HOSTNAME = 3;

    final byte addressType;
    final String hostname;
    final byte[] addressBytes;
    int port;

    private Socks5Address(byte[] addressBytes, String hostname, byte addressType) {
        this.hostname = hostname;
        this.addressType = addressType;
        this.addressBytes = addressBytes;
    }

    static String ipv4ToString(byte[] address) throws UnknownHostException {
        return Inet4Address.getByAddress(address).getHostAddress();
    }

    static String ipv6ToString(byte[] address) throws UnknownHostException {
        return Inet6Address.getByAddress(address).getHostAddress();
    }

    static Socks5Address createIpv4(byte[] ipv4Address) throws UnknownHostException {
        return new Socks5Address(ipv4Address, ipv4ToString(ipv4Address), (byte)SOCKS5_ADDRESS_IPV4);
    }

    static Socks5Address createIpv6(byte[] ipv6Address) throws UnknownHostException {
        return new Socks5Address(ipv6Address, ipv6ToString(ipv6Address), (byte)SOCKS5_ADDRESS_IPV6);
    }

    static Socks5Address createHostname(byte[] hostnameBytes) {
        String hostname = new String(hostnameBytes, StandardCharsets.UTF_8);
        return new Socks5Address(hostnameBytes, hostname, (byte)SOCKS5_ADDRESS_HOSTNAME);
    }
}
