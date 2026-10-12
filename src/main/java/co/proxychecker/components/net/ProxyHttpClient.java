package co.proxychecker.components.net;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import co.proxychecker.components.entities.Proxy;

/**
 * Minimal HTTP/1.1 GET client that can tunnel through HTTP, SOCKS4/4a and SOCKS5 proxies.
 * <p>
 * Every request uses its own socket and proxy settings, so it is safe to use from many threads at once.
 * Each read is bounded by the timeout and the whole request by twice the timeout, so a slow or
 * misbehaving proxy can't hold a worker thread forever. Response bodies are limited in size.
 */
public final class ProxyHttpClient {

    public static final int MAX_BODY_BYTES = 64 * 1024;
    private static final int MAX_LINE_BYTES = 8 * 1024;
    private static final int MAX_HEADERS = 100;

    private final int timeoutMillis;

    /**
     * An HTTP response.
     */
    public static final class Response {
        private final int status;
        private final String body;

        Response(int status, String body) {
            this.status = status;
            this.body = body;
        }

        public int getStatus() {
            return status;
        }

        public String getBody() {
            return body;
        }
    }

    public ProxyHttpClient(int timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    /**
     * Sends a GET request for an {@code http://} URI.
     *
     * @param uri     the target URI
     * @param proxy   the proxy to tunnel through, or null for a direct connection
     * @param headers additional request headers
     */
    public Response get(URI uri, Proxy proxy, Map<String, String> headers) throws IOException {
        if (!"http".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalArgumentException("Only http:// URIs are supported: " + uri);
        }
        String host = uri.getHost();
        int port = uri.getPort() == -1 ? 80 : uri.getPort();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(2L * timeoutMillis);

        try (Socket socket = new Socket()) {
            socket.setSoTimeout(timeoutMillis);
            InetSocketAddress endpoint = proxy == null
                    ? new InetSocketAddress(host, port)
                    : new InetSocketAddress(proxy.getIp(), proxy.getPort());
            if (endpoint.isUnresolved()) {
                throw new UnknownHostException(endpoint.getHostString());
            }
            socket.connect(endpoint, timeoutMillis);

            InputStream in = new BufferedInputStream(new DeadlineInputStream(socket.getInputStream(), deadline));
            OutputStream out = new BufferedOutputStream(socket.getOutputStream());

            String requestTarget = originForm(uri);
            StringBuilder extraHeaders = new StringBuilder();
            if (proxy != null) {
                switch (proxy.getProxyType()) {
                    case HTTP:
                        requestTarget = "http://" + hostHeader(host, port) + requestTarget;
                        if (proxy.hasCredentials()) {
                            extraHeaders.append("Proxy-Authorization: ")
                                    .append(basicAuth(proxy.getUsername(), proxy.getPassword())).append("\r\n");
                        }
                        break;
                    case SOCKS4:
                        Socks.connect4(in, out, host, port, proxy.getUsername());
                        break;
                    case SOCKS5:
                        Socks.connect5(in, out, host, port, proxy.getUsername(), proxy.getPassword());
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported proxy type " + proxy.getProxyType());
                }
            }

            StringBuilder request = new StringBuilder();
            request.append("GET ").append(requestTarget).append(" HTTP/1.1\r\n");
            request.append("Host: ").append(hostHeader(host, port)).append("\r\n");
            for (Map.Entry<String, String> header : headers.entrySet()) {
                request.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
            }
            request.append(extraHeaders);
            request.append("Accept: application/json\r\n");
            request.append("Connection: close\r\n\r\n");
            out.write(request.toString().getBytes(StandardCharsets.ISO_8859_1));
            out.flush();

            return readResponse(in);
        }
    }

    static String basicAuth(String username, String password) {
        String credentials = username + ":" + (password == null ? "" : password);
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private static String originForm(URI uri) {
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
    }

    private static String hostHeader(String host, int port) {
        return port == 80 ? host : host + ":" + port;
    }

    static Response readResponse(InputStream in) throws IOException {
        String statusLine;
        int status;
        do {
            statusLine = readLine(in);
            if (statusLine == null || !statusLine.startsWith("HTTP/")) {
                throw new IOException("Invalid HTTP response");
            }
            String[] parts = statusLine.split(" ", 3);
            try {
                status = Integer.parseInt(parts.length > 1 ? parts[1] : "");
            } catch (NumberFormatException e) {
                throw new IOException("Invalid HTTP status line");
            }
            long contentLength = -1;
            boolean chunked = false;
            for (int i = 0; ; i++) {
                String line = readLine(in);
                if (line == null) {
                    throw new IOException("Unexpected end of HTTP headers");
                }
                if (line.isEmpty()) {
                    break;
                }
                if (i >= MAX_HEADERS) {
                    throw new IOException("Too many HTTP headers");
                }
                int colon = line.indexOf(':');
                if (colon <= 0) {
                    continue;
                }
                String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                String value = line.substring(colon + 1).trim();
                if (name.equals("content-length")) {
                    try {
                        contentLength = Long.parseLong(value);
                    } catch (NumberFormatException e) {
                        throw new IOException("Invalid Content-Length");
                    }
                } else if (name.equals("transfer-encoding") && value.toLowerCase(Locale.ROOT).contains("chunked")) {
                    chunked = true;
                }
            }
            if (status >= 100 && status < 200) {
                continue; // informational response, the real one follows
            }
            byte[] body;
            if (chunked) {
                body = readChunked(in);
            } else if (contentLength >= 0) {
                if (contentLength > MAX_BODY_BYTES) {
                    throw new IOException("HTTP response too large");
                }
                body = Socks.readFully(in, (int) contentLength);
            } else {
                body = readToEnd(in);
            }
            return new Response(status, new String(body, StandardCharsets.UTF_8));
        } while (true);
    }

    private static byte[] readChunked(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while (true) {
            String sizeLine = readLine(in);
            if (sizeLine == null) {
                throw new IOException("Unexpected end of chunked body");
            }
            int semicolon = sizeLine.indexOf(';');
            String hex = (semicolon >= 0 ? sizeLine.substring(0, semicolon) : sizeLine).trim();
            int size;
            try {
                size = Integer.parseInt(hex, 16);
            } catch (NumberFormatException e) {
                throw new IOException("Invalid chunk size");
            }
            if (size == 0) {
                String trailer;
                while ((trailer = readLine(in)) != null && !trailer.isEmpty()) {
                    // ignore trailers
                }
                return out.toByteArray();
            }
            if (size < 0 || out.size() + size > MAX_BODY_BYTES) {
                throw new IOException("HTTP response too large");
            }
            out.write(Socks.readFully(in, size));
            readLine(in); // CRLF after the chunk
        }
    }

    private static byte[] readToEnd(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) != -1) {
            if (out.size() + read > MAX_BODY_BYTES) {
                throw new IOException("HTTP response too large");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    /**
     * Reads a CRLF (or LF) terminated line, or returns null at end of stream.
     */
    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                byte[] bytes = line.toByteArray();
                int length = bytes.length > 0 && bytes[bytes.length - 1] == '\r' ? bytes.length - 1 : bytes.length;
                return new String(bytes, 0, length, StandardCharsets.ISO_8859_1);
            }
            if (line.size() >= MAX_LINE_BYTES) {
                throw new IOException("HTTP line too long");
            }
            line.write(b);
        }
        return line.size() == 0 ? null : line.toString("ISO-8859-1");
    }

    /**
     * Fails reads once the overall request deadline has passed.
     */
    private static final class DeadlineInputStream extends FilterInputStream {
        private final long deadline;

        DeadlineInputStream(InputStream in, long deadline) {
            super(in);
            this.deadline = deadline;
        }

        private void check() throws SocketTimeoutException {
            if (System.nanoTime() - deadline > 0) {
                throw new SocketTimeoutException("Request took too long");
            }
        }

        @Override
        public int read() throws IOException {
            check();
            return super.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            check();
            return super.read(b, off, len);
        }
    }
}
