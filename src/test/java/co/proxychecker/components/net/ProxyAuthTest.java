package co.proxychecker.components.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import co.proxychecker.commands.ProxyCheckCommandAccess;
import co.proxychecker.components.RequestAPI;
import co.proxychecker.components.UserSettings;
import co.proxychecker.components.entities.Proxy;
import co.proxychecker.components.entities.ProxyStatus;
import co.proxychecker.components.entities.ProxyType;
import co.proxychecker.support.FakeServer;

/**
 * Authenticated proxies through fake HTTP, SOCKS4 and SOCKS5 servers, checked end to end with
 * {@code ProxyCheckCommand.testProxy}.
 */
class ProxyAuthTest {

    private static final String API = "http://judge.test.invalid/";

    private static Proxy check(String spec, ProxyType type) {
        Proxy proxy = new Proxy(spec, type);
        ProxyCheckCommandAccess.testProxy(proxy, new RequestAPI(new UserSettings().setTimeout(3000), API));
        return proxy;
    }

    /** HTTP proxy that requires Basic auth for alice:wonder:land. */
    private static FakeServer httpAuthProxy(AtomicReference<String> lastRequest) throws IOException {
        String expected = "Proxy-Authorization: " + ProxyHttpClient.basicAuth("alice", "wonder:land") + "\r\n";
        return new FakeServer((in, out) -> {
            String request = FakeServer.readHttpRequest(in);
            lastRequest.set(request);
            if (request.contains(expected)) {
                FakeServer.writeHttp(out, "200 OK", FakeServer.JUDGE_RESPONSE);
            } else {
                FakeServer.writeHttp(out, "407 Proxy Authentication Required", "<html>auth</html>");
            }
        });
    }

    @Test
    void httpProxyWithBasicAuth() throws Exception {
        AtomicReference<String> request = new AtomicReference<>();
        try (FakeServer proxy = httpAuthProxy(request)) {
            String port = String.valueOf(proxy.getPort());
            assertEquals(ProxyStatus.ALIVE, check("alice:wonder:land@127.0.0.1:" + port, ProxyType.HTTP).getProxyStatus());
            assertEquals(ProxyStatus.ALIVE, check("127.0.0.1:" + port + ":alice:wonder:land", ProxyType.HTTP).getProxyStatus());
            assertEquals(ProxyStatus.DEAD, check("alice:wrong@127.0.0.1:" + port, ProxyType.HTTP).getProxyStatus());
            assertEquals(ProxyStatus.DEAD, check("127.0.0.1:" + port, ProxyType.HTTP).getProxyStatus());
            assertTrue(request.get().startsWith("GET http://judge.test.invalid/?ip="));
        }
    }

    /** SOCKS5 server that requires username/password auth and accepts the given users. */
    private static FakeServer socks5AuthProxy(Map<String, String> users) throws IOException {
        return new FakeServer((in, out) -> {
            byte[] greeting = FakeServer.read(in, 2);
            byte[] methods = FakeServer.read(in, greeting[1]);
            boolean offersUserPass = false;
            for (byte m : methods) {
                offersUserPass |= m == 2;
            }
            if (!offersUserPass) {
                out.write(new byte[] {5, (byte) 0xFF});
                return;
            }
            out.write(new byte[] {5, 2});
            out.flush();
            FakeServer.read(in, 1); // sub-negotiation version
            String user = new String(FakeServer.read(in, FakeServer.read(in, 1)[0] & 0xFF), StandardCharsets.UTF_8);
            String pass = new String(FakeServer.read(in, FakeServer.read(in, 1)[0] & 0xFF), StandardCharsets.UTF_8);
            if (!pass.equals(users.get(user))) {
                out.write(new byte[] {1, 1});
                return;
            }
            out.write(new byte[] {1, 0});
            out.flush();
            byte[] request = FakeServer.read(in, 5);
            FakeServer.read(in, (request[4] & 0xFF) + 2);
            out.write(new byte[] {5, 0, 0, 1, 127, 0, 0, 1, 0, 80});
            FakeServer.readHttpRequest(in);
            // echo the authenticated user so the client side can verify no credentials were mixed up
            FakeServer.writeHttp(out, "200 OK",
                    "{\"anonymity\":\"ELITE\",\"country\":\"" + user + "\"}");
        });
    }

    @Test
    void socks5WithUsernamePassword() throws Exception {
        Map<String, String> users = new ConcurrentHashMap<>();
        users.put("bob", "builder");
        try (FakeServer proxy = socks5AuthProxy(users)) {
            Proxy ok = check("bob:builder@" + proxy.address(), ProxyType.SOCKS5);
            assertEquals(ProxyStatus.ALIVE, ok.getProxyStatus());
            assertEquals("bob", ok.getCountry());
            assertEquals(ProxyStatus.DEAD, check("bob:nope@" + proxy.address(), ProxyType.SOCKS5).getProxyStatus());
            assertEquals(ProxyStatus.DEAD, check(proxy.address(), ProxyType.SOCKS5).getProxyStatus());
        }
    }

    @Test
    void concurrentChecksUseTheirOwnCredentials() throws Exception {
        Map<String, String> users = new ConcurrentHashMap<>();
        for (int i = 0; i < 40; i++) {
            users.put("user" + i, "pass" + i);
        }
        try (FakeServer proxy = socks5AuthProxy(users)) {
            ExecutorService pool = Executors.newFixedThreadPool(20);
            List<Future<Proxy>> results = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                // every odd proxy has a wrong password
                String spec = proxy.address() + ":user" + i + ":" + (i % 2 == 0 ? "pass" + i : "bad");
                results.add(pool.submit(() -> check(spec, ProxyType.SOCKS5)));
            }
            for (int i = 0; i < 40; i++) {
                Proxy result = results.get(i).get();
                if (i % 2 == 0) {
                    assertEquals(ProxyStatus.ALIVE, result.getProxyStatus(), "user" + i);
                    assertEquals("user" + i, result.getCountry());
                } else {
                    assertEquals(ProxyStatus.DEAD, result.getProxyStatus(), "user" + i);
                }
            }
            pool.shutdown();
        }
    }

    @Test
    void socks4SendsUserNameAsUserId() throws Exception {
        AtomicReference<String> userId = new AtomicReference<>();
        try (FakeServer server = new FakeServer((InputStream in, OutputStream out) -> {
            FakeServer.read(in, 8);
            userId.set(FakeServer.readNulString(in));
            out.write(new byte[] {0, 0x5A, 0, 0, 0, 0, 0, 0}); // target is an IPv4 literal: plain SOCKS4
            FakeServer.readHttpRequest(in);
            FakeServer.writeHttp(out, "200 OK", FakeServer.JUDGE_RESPONSE);
        })) {
            Proxy proxy = new Proxy("carol:ignored@" + server.address(), ProxyType.SOCKS4);
            ProxyCheckCommandAccess.testProxy(proxy,
                    new RequestAPI(new UserSettings().setTimeout(3000), "http://127.0.0.1:80/"));
            assertEquals(ProxyStatus.ALIVE, proxy.getProxyStatus());
            assertEquals("carol", userId.get());
        }
    }
}
