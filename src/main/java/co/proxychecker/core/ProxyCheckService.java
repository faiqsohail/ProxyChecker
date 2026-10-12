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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import co.proxychecker.components.RequestAPI;
import co.proxychecker.components.entities.Proxy;
import co.proxychecker.components.net.Cancellation;
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
     * A running check, returned by {@link #check}.
     */
    public static final class CheckHandle {
        private final Object lock = new Object();
        private final Cancellation cancellation = new Cancellation();
        private final Queue<Proxy> pending = new ConcurrentLinkedQueue<>();
        private final AtomicBoolean flushScheduled = new AtomicBoolean(false);
        private final AtomicBoolean stopRequested = new AtomicBoolean(false);
        private final Executor callbackExecutor;
        private final Consumer<List<Proxy>> onResults;
        private final Runnable onComplete;
        private final List<Proxy> proxies;
        private final int threads;
        private final ProxyTester tester;
        private final int total;
        private final AtomicBoolean started = new AtomicBoolean(false);
        private ExecutorService pool;
        // guarded by lock
        private int delivered;
        private boolean finished;
        private boolean stopped;

        private CheckHandle(List<Proxy> proxies, int threads, ProxyTester tester, Executor callbackExecutor,
                            Consumer<List<Proxy>> onResults, Runnable onComplete) {
            this.proxies = new ArrayList<>(proxies);
            this.threads = threads;
            this.tester = tester;
            this.total = proxies.size();
            this.callbackExecutor = callbackExecutor;
            this.onResults = onResults;
            this.onComplete = onComplete;
        }

        /**
         * Stops the check: queued proxies are never tested, in-flight connections are closed at once
         * and results that arrive afterwards are discarded. Proxies that were already reported stay
         * reported; then {@code onComplete} runs once through the callback executor and
         * {@link #isStopped()} returns true. Does nothing if the check has already finished.
         * Safe to call from any thread, more than once.
         */
        public void stop() {
            if (isFinished() || !stopRequested.compareAndSet(false, true)) {
                return;
            }
            cancellation.cancel();
            ExecutorService current;
            synchronized (lock) {
                current = pool;
            }
            if (current != null) {
                current.shutdownNow(); // drops queued tasks, interrupts workers
            }
            try {
                callbackExecutor.execute(() -> finish(true));
            } catch (RejectedExecutionException e) {
                // the caller already shut its callback executor down, i.e. no longer wants callbacks
            }
        }

        /**
         * @return whether the check ended because {@link #stop()} was called before it completed
         */
        public boolean isStopped() {
            synchronized (lock) {
                return stopped;
            }
        }

        /**
         * @return whether {@code onComplete} has run
         */
        public boolean isFinished() {
            synchronized (lock) {
                return finished;
            }
        }

        /**
         * Waits for the worker threads to exit.
         *
         * @return true if they all exited within the timeout
         */
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            ExecutorService current;
            synchronized (lock) {
                current = pool;
            }
            return current == null || current.awaitTermination(timeout, unit);
        }

        /**
         * Starts the check. Callbacks may run before this returns.
         *
         * @throws IllegalStateException if already started
         */
        public CheckHandle start() {
            if (!started.compareAndSet(false, true)) {
                throw new IllegalStateException("Check already started");
            }
            startWorkers();
            return this;
        }

        private void startWorkers() {
            if (proxies.isEmpty()) {
                callbackExecutor.execute(() -> finish(false));
                return;
            }
            // no point starting more threads than there are proxies
            int poolSize = Math.max(1, Math.min(threads, proxies.size()));
            ExecutorService workers = Executors.newFixedThreadPool(poolSize, runnable -> {
                Thread thread = new Thread(runnable, "proxy-checker");
                thread.setDaemon(true);
                return thread;
            });
            synchronized (lock) {
                pool = workers;
            }
            try {
                for (Proxy proxy : proxies) {
                    workers.execute(() -> testOne(proxy));
                }
            } catch (RejectedExecutionException e) {
                // stop() shut the pool down while tasks were still being submitted
            }
            workers.shutdown();
        }

        private void testOne(Proxy proxy) {
            if (cancellation.isCancelled()) {
                return;
            }
            try {
                cancellation.runWith(() -> tester.test(proxy));
            } catch (RuntimeException e) {
                // never lose a result: an unexpected failure must not leave the check running forever
                LOG.log(Level.WARNING, "Unexpected error checking " + proxy, e);
                markDead(proxy, e);
            }
            if (cancellation.isCancelled()) {
                return; // the outcome is likely the result of the stop itself, e.g. a closed socket
            }
            pending.add(proxy);
            if (flushScheduled.compareAndSet(false, true)) {
                callbackExecutor.execute(this::flush);
            }
        }

        private void flush() {
            synchronized (lock) {
                flushScheduled.set(false);
                if (finished) {
                    return; // late results after a stop
                }
                deliverPending();
                if (delivered < total) {
                    return;
                }
                finished = true;
            }
            onComplete.run();
        }

        private void deliverPending() {
            List<Proxy> batch = new ArrayList<>();
            Proxy next;
            while ((next = pending.poll()) != null) {
                batch.add(next);
            }
            if (!batch.isEmpty()) {
                delivered += batch.size();
                onResults.accept(batch);
            }
        }

        private void finish(boolean byStop) {
            synchronized (lock) {
                if (finished) {
                    return;
                }
                if (byStop) {
                    // results queued before the stop still count; anything later is dropped by testOne/flush
                    deliverPending();
                    stopped = delivered < total;
                }
                finished = true;
            }
            onComplete.run();
        }
    }

    /**
     * Checks the proxies concurrently. Unless the check is stopped, every proxy is reported exactly once,
     * even if testing it throws. Results are delivered in batches through {@code callbackExecutor}: at most
     * one delivery is queued at a time, so a UI thread is never flooded with one update per proxy.
     * No results are delivered after {@code onComplete}.
     *
     * @param threads          maximum number of worker threads
     * @param callbackExecutor runs {@code onResults} and {@code onComplete} (e.g. the JavaFX thread)
     * @param onResults        receives checked proxies
     * @param onComplete       invoked once, after every proxy has been reported or after a stop
     * @return a handle to stop the check
     */
    public static CheckHandle check(List<Proxy> proxies, int threads, ProxyTester tester, Executor callbackExecutor,
                                    Consumer<List<Proxy>> onResults, Runnable onComplete) {
        return prepare(proxies, threads, tester, callbackExecutor, onResults, onComplete).start();
    }

    /**
     * Like {@link #check} but doesn't start yet, so the caller can keep the handle before any callback
     * runs. Call {@link CheckHandle#start()} to begin.
     */
    public static CheckHandle prepare(List<Proxy> proxies, int threads, ProxyTester tester,
                                      Executor callbackExecutor, Consumer<List<Proxy>> onResults,
                                      Runnable onComplete) {
        return new CheckHandle(proxies, threads, tester, callbackExecutor, onResults, onComplete);
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
        if (e instanceof Cancellation.CancelledException) {
            return "stopped";
        }
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
