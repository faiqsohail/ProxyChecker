package co.proxychecker.ProxyChecker.components;

import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;

import co.proxychecker.ProxyChecker.components.entities.AnonymityColor;
import co.proxychecker.ProxyChecker.components.entities.ProxyAnonymity;

/**
 * Manages the user configurable system settings.
 */
public class UserSettings {

    public static final int DEFAULT_TIMEOUT = 5000;
    public static final int MIN_TIMEOUT = 500;
    public static final int DEFAULT_THREADS = 100;
    public static final int MIN_THREADS = 1;
    public static final int MAX_THREADS = 1000;

    private int timeout = DEFAULT_TIMEOUT; // timeout for checking the proxy (ms)
    private int threads = DEFAULT_THREADS; // max threads
    private String ip = "0.0.0.0"; // ip address of the user
    private Proxy.Type type = Proxy.Type.HTTP;

    private List<AnonymityColor> colorScheme = defaultColorScheme();

    private static List<AnonymityColor> defaultColorScheme() {
        List<AnonymityColor> scheme = new ArrayList<>();
        scheme.add(new AnonymityColor(ProxyAnonymity.ELITE, "#b3e6b3"));
        scheme.add(new AnonymityColor(ProxyAnonymity.ANONYMOUS, "#e6e6b3"));
        scheme.add(new AnonymityColor(ProxyAnonymity.TRANSPARENT, "#ffb3b3"));
        return scheme;
    }

    /**
     * Repairs values that are missing or out of range (e.g. from a hand edited or older
     * configuration file) so the rest of the application never sees invalid settings.
     *
     * @return this
     */
    public UserSettings sanitize() {
        if (timeout < MIN_TIMEOUT) {
            timeout = DEFAULT_TIMEOUT;
        }
        if (threads < MIN_THREADS) {
            threads = DEFAULT_THREADS;
        } else if (threads > MAX_THREADS) {
            threads = MAX_THREADS;
        }
        if (type == null || type == Proxy.Type.DIRECT) {
            type = Proxy.Type.HTTP;
        }
        if (ip == null) {
            ip = "0.0.0.0";
        }
        if (colorScheme == null) {
            colorScheme = defaultColorScheme();
        } else {
            colorScheme.removeIf(c -> c == null || c.getAnonymity() == null || c.getColor() == null);
        }
        return this;
    }

    public String getIp() {
        return ip;
    }

    public UserSettings setIp(String ip) {
        this.ip = ip;
        return this;
    }

    public int getThreads() {
        return threads;
    }

    public UserSettings setThreads(int threads) {
        this.threads = threads;
        return this;
    }

    public int getTimeout() {
        return timeout;
    }

    public UserSettings setTimeout(int timeout) {
        this.timeout = timeout;
        return this;
    }

    public Proxy.Type getProxyType() {
        return type;
    }

    public UserSettings setProxyType(Proxy.Type type) {
        this.type = type;
        return this;
    }

    public List<AnonymityColor> getColorScheme() {
        return colorScheme;
    }

    /**
     * @return the configured color for the given anonymity, or null if none is configured
     */
    public String getColor(ProxyAnonymity anonymity) {
        for (AnonymityColor color : colorScheme) {
            if (color.getAnonymity() == anonymity) {
                return color.getColor();
            }
        }
        return null;
    }

    public void setColorScheme(List<AnonymityColor> colorScheme) {
        this.colorScheme = colorScheme;
    }
}
