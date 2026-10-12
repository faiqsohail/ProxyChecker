package co.proxychecker.components;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import co.proxychecker.components.entities.Proxy;
import co.proxychecker.components.entities.ProxyAnonymity;
import co.proxychecker.components.net.ProxyHttpClient;

/**
 * Makes requests to the proxy judge API server, directly or through a proxy.
 */
public class RequestAPI {

    public static final String DEFAULT_API_URL = "http://api.proxychecker.co/";
    private static final Gson GSON = new Gson();

    private final UserSettings settings;
    private final String apiUrl;

    /**
     * The response given by the API server.
     */
    public static class Response {
        /** The caller's public IP address, only present for direct (non proxied) requests. */
        public String ip;
        public String country;
        /** Anonymity of the proxy, only present for proxied requests. */
        public ProxyAnonymity anonymity;
    }

    /**
     * A successful API response together with the time it took to receive it.
     */
    public static final class Result {
        private final Response response;
        private final long responseTimeMillis;

        Result(Response response, long responseTimeMillis) {
            this.response = response;
            this.responseTimeMillis = responseTimeMillis;
        }

        public Response getResponse() {
            return response;
        }

        public long getResponseTimeMillis() {
            return responseTimeMillis;
        }
    }

    public RequestAPI(UserSettings settings) {
        this(settings, DEFAULT_API_URL);
    }

    /**
     * @param apiUrl base URL of a compatible proxy judge API ({@code http://} only)
     */
    public RequestAPI(UserSettings settings, String apiUrl) {
        this.settings = settings;
        this.apiUrl = apiUrl.endsWith("/") ? apiUrl : apiUrl + "/";
    }

    /**
     * Requests the API server, optionally through the given proxy. The response time covers the full
     * round trip (including the proxy handshake).
     *
     * @param proxy the proxy to tunnel the request through, or null for a direct request
     * @return the parsed response
     * @throws IOException if the request fails, times out, or returns anything other than a valid API response
     */
    public Result request(Proxy proxy) throws IOException {
        URI uri = toUri(proxy == null ? apiUrl : getQueryUrl(settings.getIp()));
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", "Proxy Checker v." + Settings.getApplicationVersion()
                + " - (proxychecker.co) : " + System.getProperty("os.name")
                + " v." + System.getProperty("os.version"));

        long start = System.nanoTime();
        ProxyHttpClient.Response response = new ProxyHttpClient(settings.getTimeout()).get(uri, proxy, headers);
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        if (response.getStatus() != 200) {
            throw new IOException("Unexpected HTTP status " + response.getStatus());
        }
        return new Result(parseResponse(response.getBody()), elapsed);
    }

    /**
     * Parses the JSON returned by the API server.
     *
     * @throws IOException if the body is not a valid API response
     */
    static Response parseResponse(String json) throws IOException {
        Response response;
        try {
            response = GSON.fromJson(json, Response.class);
        } catch (JsonParseException e) {
            throw new IOException("Invalid API response", e);
        }
        if (response == null) {
            throw new IOException("Empty API response");
        }
        return response;
    }

    private static URI toUri(String url) throws IOException {
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            throw new IOException("Invalid API URL: " + url, e);
        }
    }

    private String getQueryUrl(String ip) {
        if (ip == null) {
            return apiUrl;
        }
        try {
            return apiUrl + "?ip=" + URLEncoder.encode(ip, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e); // UTF-8 is always supported
        }
    }
}
