package co.proxychecker.components;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import co.proxychecker.components.entities.ProxyAnonymity;

/**
 * Makes requests to the proxy judge API server.
 */
public class RequestAPI {

    private static final String API_URL = "http://api.proxychecker.co/";
    /** Upper bound on the response body, a misbehaving proxy must not be able to exhaust memory. */
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;
    private static final Gson GSON = new Gson();

    private final UserSettings settings;

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
        this.settings = settings;
    }

    /**
     * Requests the API server, optionally through the given proxy. The connection is always released,
     * and the response time covers the full round trip (not only the TCP connect to the proxy).
     *
     * @param proxy the proxy to tunnel the request through, or null for a direct request
     * @return the parsed response
     * @throws IOException if the request fails, times out, or returns anything other than a valid API response
     */
    public Result request(Proxy proxy) throws IOException {
        URL url = new URL(proxy == null ? API_URL : getQueryUrl(settings.getIp()));
        HttpURLConnection connection = (HttpURLConnection) (proxy == null
                ? url.openConnection()
                : url.openConnection(proxy));
        try {
            connection.setRequestProperty("User-Agent", "Proxy Checker v." + Settings.getApplicationVersion()
                    + " - (proxychecker.co) : " + System.getProperty("os.name")
                    + " v." + System.getProperty("os.version"));
            connection.setRequestMethod("GET");
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(settings.getTimeout());
            connection.setReadTimeout(settings.getTimeout());

            long start = System.nanoTime();
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("Unexpected HTTP status " + status);
            }
            String body;
            try (InputStream in = connection.getInputStream()) {
                body = readLimited(in);
            }
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            return new Result(parseResponse(body), elapsed);
        } finally {
            connection.disconnect();
        }
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

    private static String readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) != -1) {
            if (out.size() + read > MAX_RESPONSE_BYTES) {
                throw new IOException("API response too large");
            }
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String getQueryUrl(String ip) {
        if (ip == null) {
            return API_URL;
        }
        try {
            return API_URL + "?ip=" + URLEncoder.encode(ip, StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e); // UTF-8 is always supported
        }
    }
}
