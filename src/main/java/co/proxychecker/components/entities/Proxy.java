package co.proxychecker.components.entities;

import java.net.Proxy.Type;

/**
 * A single proxy and the result of checking it.
 */
public class Proxy {

    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;

    private final String ip;
    private final int port;

    private Type type;
    private ProxyStatus status = ProxyStatus.DEAD; // assume the proxy is dead until checked
    private ProxyAnonymity anonymity = ProxyAnonymity.TRANSPARENT;
    private String country;
    private String responseTime;

    /**
     * Creates a proxy from a string in the format {@code host:port}.
     *
     * @param ipPort host and port, see {@link #isValidFormat(String)}
     * @param type   either HTTP or SOCKS
     * @throws IllegalArgumentException if the string is not a valid proxy
     */
    public Proxy(String ipPort, Type type) {
        String[] parts = split(ipPort);
        if (parts == null) {
            throw new IllegalArgumentException("Invalid proxy format, expected host:port but got: " + ipPort);
        }
        this.ip = parts[0];
        this.port = Integer.parseInt(parts[1]);
        this.type = type;
    }

    /**
     * Determines whether a string is a valid proxy: a non empty host, a single colon and a port between
     * 1 and 65535. Surrounding whitespace is ignored.
     */
    public static boolean isValidFormat(String ipPort) {
        return split(ipPort) != null;
    }

    /**
     * @return {host, port} or null if the string is not a valid proxy
     */
    private static String[] split(String ipPort) {
        if (ipPort == null) {
            return null;
        }
        String[] parts = ipPort.trim().split(":");
        if (parts.length != 2) {
            return null;
        }
        String host = parts[0].trim();
        String portText = parts[1].trim();
        try {
            int port = Integer.parseInt(portText);
            if (host.isEmpty() || port < MIN_PORT || port > MAX_PORT) {
                return null;
            }
            return new String[] {host, String.valueOf(port)};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public String getCountry() {
        return country;
    }

    public Proxy setCountry(String country) {
        this.country = country;
        return this;
    }

    /**
     * @return response time such as "120 (ms)", or null if the proxy is dead
     */
    public String getResponseTime() {
        return responseTime;
    }

    public Proxy setResponseTime(String responseTime) {
        this.responseTime = responseTime;
        return this;
    }

    public Type getProxyType() {
        return type;
    }

    public Proxy setProxyType(Type type) {
        this.type = type;
        return this;
    }

    public ProxyStatus getProxyStatus() {
        return status;
    }

    public Proxy setProxyStatus(ProxyStatus status) {
        this.status = status;
        return this;
    }

    /**
     * @return the anonymity level, or null if the proxy is dead
     */
    public ProxyAnonymity getProxyAnonymity() {
        return anonymity;
    }

    public Proxy setProxyAnonymity(ProxyAnonymity anonymity) {
        this.anonymity = anonymity;
        return this;
    }
}
