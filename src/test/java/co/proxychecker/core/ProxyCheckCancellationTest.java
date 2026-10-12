package co.proxychecker.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import co.proxychecker.components.RequestAPI;
import co.proxychecker.components.UserSettings;
import co.proxychecker.components.entities.Proxy;
import co.proxychecker.components.entities.ProxyStatus;
import co.proxychecker.components.entities.ProxyType;
import co.proxychecker.components.net.Cancellation;
import co.proxychecker.support.FakeServer;

/**
 * Stopping a running check: prompt, keeps what was already reported, reports nothing afterwards.
 */
class ProxyCheckCancellationTest {

    /** Single-threaded like the JavaFX thread. */
    private final ExecutorService callbacks = Executors.newSingleThreadExecutor();

    @AfterEach
    void tearDown() {
        callbacks.shutdownNow();
    }

    private static List<Proxy> proxies(int count, int firstPort) {
        List<Proxy> proxies = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            proxies.add(new Proxy("127.0.0.1:" + (firstPort + i), ProxyType.HTTP));
        }
        return proxies;
    }

    @Test
    void stopClosesInFlightConnectionsAndKeepsPartialResults() throws Exception {
        AtomicInteger connections = new AtomicInteger();
        CountDownLatch slowConnected = new CountDownLatch(3);
        CountDownLatch slowClosedByClient = new CountDownLatch(3);
        // a proxy that accepts the request and never answers
        try (FakeServer slow = new FakeServer((in, out) -> {
            connections.incrementAndGet();
            slowConnected.countDown();
            while (in.read() != -1) {
                // wait for the client to hang up
            }
            slowClosedByClient.countDown();
        })) {
            List<Proxy> proxies = proxies(5, 1); // "fast" ones, answered without the network
            for (int i = 0; i < 3; i++) {
                proxies.add(new Proxy(slow.address(), ProxyType.HTTP));
            }
            proxies.addAll(proxies(20, 100)); // queued behind the slow ones, never started
            UserSettings settings = new UserSettings().setTimeout(60_000).setIp("9.9.9.9");
            RequestAPI api = new RequestAPI(settings, "http://127.0.0.1:1/");
            ProxyCheckService.ProxyTester tester = proxy -> {
                if (proxy.getPort() < 100 && proxy.getPort() != slow.getPort()) {
                    proxy.setProxyStatus(ProxyStatus.ALIVE);
                } else {
                    ProxyCheckService.testProxy(proxy, api);
                }
            };

            List<Proxy> results = Collections.synchronizedList(new ArrayList<>());
            AtomicInteger completions = new AtomicInteger();
            AtomicInteger resultsAfterComplete = new AtomicInteger();
            AtomicBoolean complete = new AtomicBoolean();
            CountDownLatch done = new CountDownLatch(1);
            ProxyCheckService.CheckHandle handle = ProxyCheckService.check(proxies, 8, tester, callbacks,
                    batch -> {
                        if (complete.get()) {
                            resultsAfterComplete.addAndGet(batch.size());
                        }
                        results.addAll(batch);
                    },
                    () -> {
                        complete.set(true);
                        completions.incrementAndGet();
                        done.countDown();
                    });

            assertTrue(slowConnected.await(10, TimeUnit.SECONDS), "slow proxies reached");
            long start = System.nanoTime();
            handle.stop();
            assertTrue(done.await(2, TimeUnit.SECONDS), "completes promptly after stop");
            assertTrue(handle.awaitTermination(2, TimeUnit.SECONDS), "worker threads exit");
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertTrue(elapsedMillis < 2000, "stopped in " + elapsedMillis + " ms, not after the 60 s timeout");
            assertTrue(slowClosedByClient.await(2, TimeUnit.SECONDS), "in-flight connections were closed");

            assertTrue(handle.isStopped());
            assertTrue(handle.isFinished());
            assertEquals(1, completions.get());
            // the slow ones are neither reported nor marked dead by the stop itself
            for (Proxy proxy : results) {
                assertTrue(proxy.getPort() != slow.getPort(), "a stopped proxy was reported");
            }
            assertTrue(results.size() <= 25);
            assertEquals(3, connections.get(), "queued proxies were never started");

            int reported = results.size();
            Thread.sleep(300);
            handle.stop(); // a second stop is harmless
            Thread.sleep(200);
            assertEquals(reported, results.size(), "no results after stop");
            assertEquals(0, resultsAfterComplete.get());
            assertEquals(1, completions.get());
        }
    }

    @Test
    void lateResultsAfterStopAreDiscarded() throws Exception {
        CountDownLatch inTester = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger tested = new AtomicInteger();
        // a tester that ignores interrupts and finishes only after the stop
        ProxyCheckService.ProxyTester stubborn = proxy -> {
            tested.incrementAndGet();
            inTester.countDown();
            boolean interrupted = false;
            while (true) {
                try {
                    release.await();
                    break;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            proxy.setProxyStatus(ProxyStatus.ALIVE);
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        };
        List<Proxy> results = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch done = new CountDownLatch(1);
        ProxyCheckService.CheckHandle handle = ProxyCheckService.check(proxies(50, 1), 2, stubborn, callbacks,
                results::addAll, done::countDown);

        assertTrue(inTester.await(5, TimeUnit.SECONDS));
        handle.stop();
        assertTrue(done.await(2, TimeUnit.SECONDS));
        release.countDown(); // the two in-flight tests now "succeed"
        assertTrue(handle.awaitTermination(2, TimeUnit.SECONDS));
        callbacks.submit(() -> { }).get(2, TimeUnit.SECONDS); // drain any late flush

        assertTrue(results.isEmpty(), "late results were delivered: " + results);
        assertEquals(2, tested.get(), "queued proxies were tested after stop");
        assertTrue(handle.isStopped());
    }

    @Test
    void stopAfterCompletionChangesNothing() throws Exception {
        List<Proxy> results = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger completions = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(1);
        ProxyCheckService.CheckHandle handle = ProxyCheckService.check(proxies(10, 1), 4,
                proxy -> proxy.setProxyStatus(ProxyStatus.DEAD), callbacks, results::addAll, () -> {
                    completions.incrementAndGet();
                    done.countDown();
                });
        assertTrue(done.await(5, TimeUnit.SECONDS));
        handle.stop();
        callbacks.submit(() -> { }).get(2, TimeUnit.SECONDS);
        assertFalse(handle.isStopped());
        assertEquals(10, results.size());
        assertEquals(1, completions.get());
    }

    @Test
    void stopRacingCompletionNeverDoubleCompletesOrDeliversLate() throws Exception {
        for (int round = 0; round < 200; round++) {
            AtomicInteger completions = new AtomicInteger();
            AtomicInteger late = new AtomicInteger();
            AtomicBoolean complete = new AtomicBoolean();
            CountDownLatch done = new CountDownLatch(1);
            // a direct executor: callbacks run concurrently on the worker threads
            ProxyCheckService.CheckHandle handle = ProxyCheckService.check(proxies(20, 1), 8,
                    proxy -> proxy.setProxyStatus(ProxyStatus.ALIVE), Runnable::run,
                    batch -> {
                        if (complete.get()) {
                            late.incrementAndGet();
                        }
                    },
                    () -> {
                        complete.set(true);
                        completions.incrementAndGet();
                        done.countDown();
                    });
            handle.stop();
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertTrue(handle.awaitTermination(5, TimeUnit.SECONDS));
            assertEquals(1, completions.get(), "round " + round);
            assertEquals(0, late.get(), "round " + round);
        }
    }

    @Test
    void cancellationClosesResourcesRegisteredBeforeAndAfter() {
        Cancellation cancellation = new Cancellation();
        AtomicInteger closed = new AtomicInteger();
        Closeable resource = closed::incrementAndGet;
        assertDoesNotThrow(() -> cancellation.register(resource));
        cancellation.cancel();
        assertEquals(1, closed.get());
        try {
            cancellation.register(resource);
            throw new AssertionError("expected CancelledException");
        } catch (Cancellation.CancelledException expected) {
            assertEquals(2, closed.get());
        }
        assertTrue(cancellation.isCancelled());
        assertEquals("stopped", ProxyCheckService.describe(new Cancellation.CancelledException()));
    }

    private interface Action {
        void run() throws IOException;
    }

    private static void assertDoesNotThrow(Action action) {
        try {
            action.run();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
