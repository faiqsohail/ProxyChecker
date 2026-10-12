package co.proxychecker.components.entities;

import java.util.Locale;

import com.google.gson.annotations.SerializedName;

/**
 * Supported proxy protocols.
 */
public enum ProxyType {
    HTTP,
    /** SOCKS4, falling back to SOCKS4a (remote DNS) when the target host can't be resolved locally. */
    SOCKS4,
    /** SOCKS5. Configuration files written before v1.3 stored this as "SOCKS". */
    @SerializedName(value = "SOCKS5", alternate = {"SOCKS"})
    SOCKS5;

    /**
     * Parses a proxy type case-insensitively, also accepting "socks" (SOCKS5) and "socks4a" (SOCKS4).
     *
     * @throws IllegalArgumentException if the value isn't a known proxy type
     */
    public static ProxyType fromString(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        switch (normalized) {
            case "HTTP":
                return HTTP;
            case "SOCKS4":
            case "SOCKS4A":
                return SOCKS4;
            case "SOCKS":
            case "SOCKS5":
                return SOCKS5;
            default:
                throw new IllegalArgumentException("Unknown proxy type: " + value);
        }
    }
}
