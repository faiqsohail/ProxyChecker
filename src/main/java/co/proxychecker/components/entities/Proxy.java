package co.proxychecker.components.entities;

/**
 * A single proxy and the result of checking it.
 * <p>
 * Accepted formats: {@code host:port}, {@code user:pass@host:port} and {@code host:port:user:pass}.
 */
public class Proxy {

    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;

    private final String spec;
    private final String ip;
    private final int port;
    private final String username;
    private final String password;

    private ProxyType type;
    private ProxyStatus status = ProxyStatus.DEAD; // assume the proxy is dead until checked
    private ProxyAnonymity anonymity = ProxyAnonymity.TRANSPARENT;
    private String country;
    private String responseTime;

    /**
     * Parsed parts of a proxy string.
     */
    private static final class Parts {
        final String host;
        final int port;
        final String username;
        final String password;

        Parts(String host, int port, String username, String password) {
            this.host = host;
            this.port = port;
            this.username = username;
            this.password = password;
        }
    }

    /**
     * Creates a proxy from a string in one of the accepted formats.
     *
     * @param spec proxy string, see {@link #isValidFormat(String)}
     * @param type the proxy protocol
     * @throws IllegalArgumentException if the string is not a valid proxy
     */
    public Proxy(String spec, ProxyType type) {
        Parts parts = parse(spec);
        if (parts == null) {
            throw new IllegalArgumentException("Invalid proxy format, expected host:port, user:pass@host:port "
                    + "or host:port:user:pass");
        }
        this.spec = spec.trim();
        this.ip = parts.host;
        this.port = parts.port;
        this.username = parts.username;
        this.password = parts.password;
        this.type = type;
    }

    /**
     * Determines whether a string is a valid proxy: {@code host:port}, {@code user:pass@host:port} or
     * {@code host:port:user:pass}, with a non empty host and user name and a port between 1 and 65535.
     * Surrounding whitespace is ignored. Passwords may contain ':' (and '@' in the second format).
     */
    public static boolean isValidFormat(String spec) {
        return parse(spec) != null;
    }

    private static Parts parse(String spec) {
        if (spec == null) {
            return null;
        }
        String value = spec.trim();
        int at = value.lastIndexOf('@');
        if (at >= 0) { // user:pass@host:port
            String credentials = value.substring(0, at);
            int colon = credentials.indexOf(':');
            if (colon <= 0) {
                return null;
            }
            String[] hostPort = value.substring(at + 1).split(":", -1);
            if (hostPort.length != 2) {
                return null;
            }
            return parts(hostPort[0], hostPort[1], credentials.substring(0, colon), credentials.substring(colon + 1));
        }
        String[] fields = value.split(":", 4);
        if (fields.length == 2) { // host:port
            return parts(fields[0], fields[1], null, null);
        }
        if (fields.length == 4 && !fields[2].isEmpty()) { // host:port:user:pass
            return parts(fields[0], fields[1], fields[2], fields[3]);
        }
        return null;
    }

    private static Parts parts(String host, String portText, String username, String password) {
        String trimmedHost = host.trim();
        try {
            int port = Integer.parseInt(portText.trim());
            if (trimmedHost.isEmpty() || trimmedHost.contains(" ") || port < MIN_PORT || port > MAX_PORT) {
                return null;
            }
            return new Parts(trimmedHost, port, username, password);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * @return the proxy exactly as it was loaded (including credentials, if any)
     */
    public String getSpec() {
        return spec;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    /**
     * @return the user name, or null if the proxy has no credentials
     */
    public String getUsername() {
        return username;
    }

    /**
     * @return the password, or null if the proxy has no credentials
     */
    public String getPassword() {
        return password;
    }

    public boolean hasCredentials() {
        return username != null;
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

    public ProxyType getProxyType() {
        return type;
    }

    public Proxy setProxyType(ProxyType type) {
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

    /**
     * @return {@code host:port}, never including credentials (safe for logs)
     */
    @Override
    public String toString() {
        return ip + ":" + port;
    }
}
