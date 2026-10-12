package co.proxychecker.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import co.proxychecker.components.RequestAPI;
import co.proxychecker.components.UserSettings;
import co.proxychecker.components.entities.Proxy;
import co.proxychecker.components.entities.ProxyAnonymity;
import co.proxychecker.components.entities.ProxyStatus;

class ProxyCheckCommandTest {

    @Test
    void reportsEveryProxyAndCompletesOnceEvenWhenTesterFails() throws InterruptedException {
        List<String> proxies = new ArrayList<>();
        for (int i = 1; i <= 200; i++) {
            proxies.add("10.0.0." + (i % 250 + 1) + ":" + i);
        }
        List<Proxy> results = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger completions = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(1);

        boolean started = ProxyCheckCommand.check(proxies, new UserSettings().setThreads(8),
                proxy -> {
                    if (proxy.getPort() % 3 == 0) {
                        throw new IllegalStateException("boom");
                    }
                    proxy.setProxyStatus(ProxyStatus.ALIVE);
                },
                Runnable::run, results::addAll, () -> {
                    completions.incrementAndGet();
                    done.countDown();
                });

        assertTrue(started);
        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertEquals(200, results.size());
        assertEquals(1, completions.get());
        assertFalse(ProxyCheckCommand.isRunning());
        long dead = results.stream().filter(p -> p.getProxyStatus() == ProxyStatus.DEAD).count();
        assertEquals(66, dead);
    }

    @Test
    void refusesToStartWhileRunning() throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        ProxyCheckCommand.check(Collections.singletonList("1.2.3.4:80"), new UserSettings(),
                proxy -> {
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }, Runnable::run, r -> { }, done::countDown);

        assertTrue(ProxyCheckCommand.isRunning());
        assertFalse(ProxyCheckCommand.check(Collections.singletonList("1.2.3.4:80"), new UserSettings(),
                proxy -> { }, Runnable::run, r -> { }, () -> { }));
        release.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertFalse(ProxyCheckCommand.isRunning());
    }

    @Test
    void completesImmediatelyWhenNothingToCheck() {
        AtomicInteger completions = new AtomicInteger();
        assertTrue(ProxyCheckCommand.check(Arrays.asList("invalid", "1.2.3.4:99999"), new UserSettings(),
                proxy -> { }, Runnable::run, r -> { }, completions::incrementAndGet));
        assertEquals(1, completions.get());
        assertFalse(ProxyCheckCommand.isRunning());
    }

    @Test
    void marksProxyAliveOnlyForValidApiResponse() throws Exception {
        String valid = "{\"anonymity\":\"ELITE\",\"country\":\"Canada\",\"connected_ip\":\"1.1.1.1\"}";
        Proxy alive = checkThroughFakeProxy(httpResponse("200 OK", valid));
        assertEquals(ProxyStatus.ALIVE, alive.getProxyStatus());
        assertEquals(ProxyAnonymity.ELITE, alive.getProxyAnonymity());
        assertEquals("Canada", alive.getCountry());

        // proxies that answer with their own page, an error, or unrelated JSON are not working proxies
        assertEquals(ProxyStatus.DEAD, checkThroughFakeProxy(httpResponse("200 OK", "<html>login</html>"))
                .getProxyStatus());
        assertEquals(ProxyStatus.DEAD, checkThroughFakeProxy(httpResponse("403 Forbidden", valid))
                .getProxyStatus());
        assertEquals(ProxyStatus.DEAD, checkThroughFakeProxy(httpResponse("200 OK", "{\"foo\":1}"))
                .getProxyStatus());
    }

    @Test
    void marksUnreachableProxyDead() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        Proxy proxy = new Proxy("127.0.0.1:" + port, java.net.Proxy.Type.HTTP);
        ProxyCheckCommand.testProxy(proxy, new RequestAPI(new UserSettings().setTimeout(1000)));
        assertEquals(ProxyStatus.DEAD, proxy.getProxyStatus());
    }

    private static String httpResponse(String status, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return "HTTP/1.1 " + status + "\r\nContent-Type: application/json\r\nContent-Length: " + bytes.length
                + "\r\nConnection: close\r\n\r\n" + body;
    }

    /** Runs a check through a local fake HTTP proxy that always answers with the given raw response. */
    private static Proxy checkThroughFakeProxy(String rawResponse) throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            Thread thread = new Thread(() -> {
                try (Socket client = server.accept()) {
                    InputStream in = client.getInputStream();
                    // consume the request headers
                    int matched = 0;
                    byte[] terminator = {'\r', '\n', '\r', '\n'};
                    int b;
                    while (matched < 4 && (b = in.read()) != -1) {
                        matched = (b == terminator[matched]) ? matched + 1 : (b == '\r' ? 1 : 0);
                    }
                    OutputStream out = client.getOutputStream();
                    out.write(rawResponse.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                } catch (Exception ignored) {
                    // test server
                }
            });
            thread.setDaemon(true);
            thread.start();

            Proxy proxy = new Proxy("127.0.0.1:" + server.getLocalPort(), java.net.Proxy.Type.HTTP);
            ProxyCheckCommand.testProxy(proxy, new RequestAPI(new UserSettings().setTimeout(5000)));
            thread.join(5000);
            return proxy;
        }
    }
}
