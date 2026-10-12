package co.proxychecker.components.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import co.proxychecker.components.entities.Proxy;
import co.proxychecker.components.entities.ProxyType;
import co.proxychecker.support.FakeServer;

class ProxyHttpClientTest {

    private static final URI TARGET = URI.create("http://judge.test.invalid/?ip=9.9.9.9");
    private final ProxyHttpClient client = new ProxyHttpClient(3000);

    @Test
    void directRequest() throws Exception {
        AtomicReference<String> request = new AtomicReference<>();
        try (FakeServer server = new FakeServer((in, out) -> {
            request.set(FakeServer.readHttpRequest(in));
            FakeServer.writeHttp(out, "200 OK", "{\"ip\":\"9.9.9.9\"}");
        })) {
            ProxyHttpClient.Response response = client.get(
                    URI.create("http://127.0.0.1:" + server.getPort() + "/"), null,
                    Collections.singletonMap("User-Agent", "test"));
            assertEquals(200, response.getStatus());
            assertEquals("{\"ip\":\"9.9.9.9\"}", response.getBody());
            assertTrue(request.get().startsWith("GET / HTTP/1.1\r\n"), request.get());
            assertTrue(request.get().contains("User-Agent: test\r\n"));
        }
    }

    @Test
    void httpProxyUsesAbsoluteUri() throws Exception {
        AtomicReference<String> request = new AtomicReference<>();
        try (FakeServer proxy = new FakeServer((in, out) -> {
            request.set(FakeServer.readHttpRequest(in));
            FakeServer.writeHttp(out, "200 OK", FakeServer.JUDGE_RESPONSE);
        })) {
            ProxyHttpClient.Response response = client.get(TARGET,
                    new Proxy(proxy.address(), ProxyType.HTTP), Collections.emptyMap());
            assertEquals(FakeServer.JUDGE_RESPONSE, response.getBody());
            assertTrue(request.get().startsWith("GET http://judge.test.invalid/?ip=9.9.9.9 HTTP/1.1\r\n"));
            assertTrue(request.get().contains("Host: judge.test.invalid\r\n"));
        }
    }

    @Test
    void socks4UsesResolvedAddress() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        try (FakeServer proxy = new FakeServer((in, out) -> {
            byte[] header = FakeServer.read(in, 8);
            String userId = FakeServer.readNulString(in);
            seen.set(header[0] + "," + header[1] + "," + (((header[2] & 0xFF) << 8) | (header[3] & 0xFF)) + ","
                    + (header[4] & 0xFF) + "." + (header[5] & 0xFF) + "." + (header[6] & 0xFF) + "."
                    + (header[7] & 0xFF) + ",user=" + userId);
            out.write(new byte[] {0, 0x5A, 0, 0, 0, 0, 0, 0});
            String request = FakeServer.readHttpRequest(in);
            FakeServer.writeHttp(out, request.startsWith("GET /?ip=1 HTTP/1.1") ? "200 OK" : "400 Bad", "{}");
        })) {
            ProxyHttpClient.Response response = client.get(URI.create("http://127.0.0.1:8080/?ip=1"),
                    new Proxy(proxy.address(), ProxyType.SOCKS4), Collections.emptyMap());
            assertEquals(200, response.getStatus());
            assertEquals("4,1,8080,127.0.0.1,user=", seen.get());
        }
    }

    @Test
    void socks4aSendsHostNameWhenUnresolvable() throws Exception {
        java.io.ByteArrayOutputStream sent = new java.io.ByteArrayOutputStream();
        InputStream reply = new ByteArrayInputStream(new byte[] {0, 0x5A, 0, 0, 0, 0, 0, 0});
        Socks.connect4(reply, sent, "judge.test.invalid", 80, null, host -> null);
        byte[] expected = "\u0004\u0001\u0000P\u0000\u0000\u0000\u0001\u0000judge.test.invalid\u0000"
                .getBytes(StandardCharsets.ISO_8859_1);
        org.junit.jupiter.api.Assertions.assertArrayEquals(expected, sent.toByteArray());
    }

    @Test
    void socks4RejectionFails() throws Exception {
        try (FakeServer proxy = new FakeServer((in, out) -> {
            FakeServer.read(in, 8);
            FakeServer.readNulString(in);
            out.write(new byte[] {0, 0x5B, 0, 0, 0, 0, 0, 0});
        })) {
            IOException e = assertThrows(IOException.class, () -> client.get(URI.create("http://127.0.0.1/"),
                    new Proxy(proxy.address(), ProxyType.SOCKS4), Collections.emptyMap()));
            assertTrue(e.getMessage().contains("rejected"), e.getMessage());
        }
    }

    @Test
    void socks5ConnectsByDomainName() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        try (FakeServer proxy = new FakeServer((in, out) -> {
            byte[] greeting = FakeServer.read(in, 2);
            FakeServer.read(in, greeting[1]);
            out.write(new byte[] {5, 0});
            byte[] request = FakeServer.read(in, 5);
            String host = new String(FakeServer.read(in, request[4]), StandardCharsets.US_ASCII);
            byte[] port = FakeServer.read(in, 2);
            seen.set(request[1] + "," + request[3] + "," + host + ":" + (((port[0] & 0xFF) << 8) | (port[1] & 0xFF)));
            out.write(new byte[] {5, 0, 0, 1, 127, 0, 0, 1, 0, 80});
            FakeServer.readHttpRequest(in);
            FakeServer.writeHttp(out, "200 OK", FakeServer.JUDGE_RESPONSE);
        })) {
            ProxyHttpClient.Response response = client.get(TARGET,
                    new Proxy(proxy.address(), ProxyType.SOCKS5), Collections.emptyMap());
            assertEquals(FakeServer.JUDGE_RESPONSE, response.getBody());
            assertEquals("1,3,judge.test.invalid:80", seen.get());
        }
    }

    @Test
    void socks5RequiringAuthFailsWithoutCredentials() throws Exception {
        try (FakeServer proxy = new FakeServer((in, out) -> {
            byte[] greeting = FakeServer.read(in, 2);
            FakeServer.read(in, greeting[1]);
            out.write(new byte[] {5, (byte) 0xFF});
        })) {
            assertThrows(IOException.class, () -> client.get(TARGET,
                    new Proxy(proxy.address(), ProxyType.SOCKS5), Collections.emptyMap()));
        }
    }

    @Test
    void parsesChunkedResponses() throws IOException {
        String raw = "HTTP/1.1 100 Continue\r\n\r\nHTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n"
                + "4\r\n{\"ip\r\na;ext=1\r\n\":\"1.2.3\"}\r\n0\r\n\r\n";
        ProxyHttpClient.Response response = ProxyHttpClient.readResponse(stream(raw));
        assertEquals(200, response.getStatus());
        assertEquals("{\"ip\":\"1.2.3\"}", response.getBody());
    }

    @Test
    void readsUntilEndWithoutContentLength() throws IOException {
        ProxyHttpClient.Response response = ProxyHttpClient.readResponse(stream("HTTP/1.0 403 Forbidden\nX: y\n\nno"));
        assertEquals(403, response.getStatus());
        assertEquals("no", response.getBody());
    }

    @Test
    void rejectsGarbageAndOversizedResponses() {
        assertThrows(IOException.class, () -> ProxyHttpClient.readResponse(stream("SSH-2.0-OpenSSH\r\n")));
        assertThrows(IOException.class, () -> ProxyHttpClient.readResponse(
                stream("HTTP/1.1 200 OK\r\nContent-Length: 999999999\r\n\r\n")));
    }

    @Test
    void slowProxyTimesOut() throws Exception {
        try (FakeServer proxy = new FakeServer((in, out) -> {
            FakeServer.readHttpRequest(in);
            out.write("HTTP/1.1 200 OK\r\n".getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        })) {
            long start = System.nanoTime();
            assertThrows(IOException.class, () -> new ProxyHttpClient(500).get(TARGET,
                    new Proxy(proxy.address(), ProxyType.HTTP), Collections.emptyMap()));
            assertTrue((System.nanoTime() - start) / 1_000_000 < 3000);
        }
    }

    private static InputStream stream(String raw) {
        return new ByteArrayInputStream(raw.getBytes(StandardCharsets.ISO_8859_1));
    }
}
