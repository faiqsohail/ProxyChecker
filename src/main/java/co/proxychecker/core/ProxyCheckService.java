package co.proxychecker.core;

import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import co.proxychecker.components.RequestAPI;
import co.proxychecker.components.entities.Proxy;
import co.proxychecker.components.net.ProxyFailure;
import co.proxychecker.components.entities.ProxyStatus;
import co.proxychecker.components.entities.ProxyType;

/**
 * The proxy checking engine shared by the GUI and the command line. Has no JavaFX dependency.
 */
public final class ProxyCheckService {

    private static final Logger LOG = Logger.getLogger(ProxyCheckService.class.getName());

    /**
     * Tests a single proxy and records the outcome on it.
     */
    @FunctionalInterface
    public interface ProxyTester {
        void test(Proxy proxy);
    }

    private ProxyCheckService() {
    }

    /**
     * @return a tester that checks proxies against the judge API
     */
    public static ProxyTester apiTester(RequestAPI requestAPI) {
        return proxy -> testProxy(proxy, requestAPI);
    }

    /**
     * Parses proxy strings, skipping invalid ones.
     */
    public static List<Proxy> parse(List<String> specs, ProxyType type) {
        List<Proxy> proxies = new ArrayList<>(specs.size());
        for (String spec : specs) {
            if (Proxy.isValidFormat(spec)) {
                proxies.add(new Proxy(spec, type));
            } else {
                LOG.warning("Skipping invalid proxy entry");
            }
        }
        return proxies;
    }

    /**
     * Checks the proxies concurrently. Every proxy is always reported exactly once, even if testing it
     * throws. Results are delivered in batches through {@code callbackExecutor}: at most one delivery is
     * queued at a time, so a UI thread is never flooded with one update per proxy.
     *
     * @param threads          maximum number of worker threads
     * @param callbackExecutor runs {@code onResults} and {@code onComplete} (e.g. the JavaFX thread)
     * @param onResults        receives checked proxies
     * @param onComplete       invoked once after every proxy has been reported
     */
    public static void check(List<Proxy> proxies, int threads, ProxyTester tester, Executor callbackExecutor,
                             Consumer<List<Proxy>> onResults, Runnable onComplete) {
        if (proxies.isEmpty()) {
            callbackExecutor.execute(onComplete);
            return;
        }
        AtomicInteger remaining = new AtomicInteger(proxies.size());
        Queue<Proxy> pending = new ConcurrentLinkedQueue<>();
        AtomicBoolean flushScheduled = new AtomicBoolean(false);
        Runnable flush = () -> {
            flushScheduled.set(false);
            List<Proxy> batch = new ArrayList<>();
            Proxy next;
            while ((next = pending.poll()) != null) {
                batch.add(next);
            }
            if (!batch.isEmpty()) {
                onResults.accept(batch);
                if (remaining.addAndGet(-batch.size()) == 0) {
                    onComplete.run();
                }
            }
        };

        // no point starting more threads than there are proxies
        int poolSize = Math.max(1, Math.min(threads, proxies.size()));
        ExecutorService pool = Executors.newFixedThreadPool(poolSize, runnable -> {
            Thread thread = new Thread(runnable, "proxy-checker");
            thread.setDaemon(true);
            return thread;
        });
        for (Proxy proxy : proxies) {
            pool.execute(() -> {
                try {
                    tester.test(proxy);
                } catch (RuntimeException e) {
                    // never lose a result: an unexpected failure must not leave the check running forever
                    LOG.log(Level.WARNING, "Unexpected error checking " + proxy, e);
                    markDead(proxy, e);
                }
                pending.add(proxy);
                if (flushScheduled.compareAndSet(false, true)) {
                    callbackExecutor.execute(flush);
                }
            });
        }
        pool.shutdown();
    }

    /**
     * Checks a proxy against the API server. The proxy is only considered alive if the request
     * through it returns a valid API response that includes the anonymity level; anything else
     * (timeouts, errors, failed authentication, pages served by the proxy itself) marks it dead.
     */
    public static void testProxy(Proxy proxy, RequestAPI requestAPI) {
        try {
            RequestAPI.Result result = requestAPI.request(proxy);
            if (result.getResponse().anonymity == null) {
                throw new ProxyFailure(ProxyFailure.BAD_API_RESPONSE, "API response did not contain an anonymity level");
            }
            proxy.setProxyStatus(ProxyStatus.ALIVE)
                    .setProxyAnonymity(result.getResponse().anonymity)
                    .setCountry(result.getResponse().country)
                    .setResponseTimeMillis(result.getResponseTimeMillis())
                    .setFailureReason(null);
        } catch (IOException | IllegalArgumentException e) {
            markDead(proxy, e);
        }
    }

    private static void markDead(Proxy proxy, Throwable cause) {
        proxy.setProxyStatus(ProxyStatus.DEAD).setProxyAnonymity(null).setFailureReason(describe(cause));
    }

    /**
     * Turns a failure into a short reason for users, e.g. "timeout", "connection refused" or "auth failed".
     */
    public static String describe(Throwable e) {
        if (e instanceof ProxyFailure) {
            return ((ProxyFailure) e).getReason();
        }
        if (e instanceof SocketTimeoutException) {
            return "timeout";
        }
        if (e instanceof UnknownHostException) {
            return "unknown host";
        }
        if (e instanceof NoRouteToHostException) {
            return "no route to host";
        }
        if (e instanceof ConnectException) {
            String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
            return message.contains("timed out") ? "timeout" : "connection refused";
        }
        if (e instanceof EOFException) {
            return "connection closed";
        }
        if (e instanceof SocketException) {
            String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
            return message.contains("reset") ? "connection reset" : "network error";
        }
        if (e instanceof IllegalArgumentException) {
            return "invalid proxy";
        }
        return "error: " + e.getClass().getSimpleName();
    }
}
