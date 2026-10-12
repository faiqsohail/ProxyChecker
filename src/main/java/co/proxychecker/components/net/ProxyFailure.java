package co.proxychecker.components.net;

import java.io.IOException;

/**
 * A failed check with a short, user facing reason such as "auth failed" or "bad handshake".
 * The message keeps the technical detail.
 */
public class ProxyFailure extends IOException {

    private static final long serialVersionUID = 1L;

    public static final String AUTH_FAILED = "auth failed";
    public static final String AUTH_REQUIRED = "auth required";
    public static final String BAD_HANDSHAKE = "bad handshake";
    public static final String REJECTED = "rejected by proxy";
    public static final String BAD_HTTP_RESPONSE = "bad HTTP response";
    public static final String BAD_API_RESPONSE = "bad API response";

    private final String reason;

    public ProxyFailure(String reason, String detail) {
        super(detail);
        this.reason = reason;
    }

    public ProxyFailure(String reason, String detail, Throwable cause) {
        super(detail, cause);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
