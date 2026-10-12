package co.proxychecker.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.application.Platform;

import co.proxychecker.components.RequestAPI;
import co.proxychecker.components.Settings;
import co.proxychecker.components.UserSettings;
import co.proxychecker.components.entities.Proxy;
import co.proxychecker.components.entities.ProxyStatus;

/**
 * Checks a list of proxies concurrently and reports the results back on the JavaFX application thread.
 */
public final class ProxyCheckCommand {

    private static final Logger LOG = Logger.getLogger(ProxyCheckCommand.class.getName());
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    /**
     * Tests a single proxy and records the outcome on it.
     */
    @FunctionalInterface
    interface ProxyTester {
        void test(Proxy proxy);
    }

    private ProxyCheckCommand() {
    }

    /**
     * @return whether proxies are currently being checked
     */
    public static boolean isRunning() {
        return RUNNING.get();
    }

    /**
     * Starts checking the given proxies (in the format host:port) using the current settings.
     *
     * @param proxies    proxies to check
     * @param onResults  receives checked proxies, invoked on the JavaFX application thread
     * @param onComplete invoked once on the JavaFX application thread after every proxy has been reported
     * @return false if a check is already running and nothing was started
     */
    public static boolean check(List<String> proxies, Consumer<List<Proxy>> onResults, Runnable onComplete) {
        UserSettings settings = Settings.getConfig();
        RequestAPI requestAPI = new RequestAPI(settings);
        return check(proxies, settings, proxy -> testProxy(proxy, requestAPI), Platform::runLater,
                onResults, onComplete);
    }

    static boolean check(List<String> proxies, UserSettings settings, ProxyTester tester, Executor uiExecutor,
                         Consumer<List<Proxy>> onResults, Runnable onComplete) {
        if (!RUNNING.compareAndSet(false, true)) {
            return false;
        }

        List<Proxy> toCheck = new ArrayList<>(proxies.size());
        for (String value : proxies) {
            if (Proxy.isValidFormat(value)) {
                toCheck.add(new Proxy(value, settings.getProxyType()));
            } else {
                LOG.warning("Skipping invalid proxy entry");
            }
        }
        if (toCheck.isEmpty()) {
            RUNNING.set(false);
            uiExecutor.execute(onComplete);
            return true;
        }

        AtomicInteger remaining = new AtomicInteger(toCheck.size());
        Queue<Proxy> pending = new ConcurrentLinkedQueue<>();
        AtomicBoolean flushScheduled = new AtomicBoolean(false);
        // results are delivered in batches: at most one UI update is queued at a time, instead of one
        // Platform.runLater per proxy which floods the FX thread and re-renders the table per row
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
                    RUNNING.set(false);
                    onComplete.run();
                }
            }
        };

        // no point starting more threads than there are proxies
        int threads = Math.max(1, Math.min(settings.getThreads(), toCheck.size()));
        ExecutorService pool = Executors.newFixedThreadPool(threads, runnable -> {
            Thread thread = new Thread(runnable, "proxy-checker");
            thread.setDaemon(true);
            return thread;
        });
        for (Proxy proxy : toCheck) {
            pool.execute(() -> {
                try {
                    tester.test(proxy);
                } catch (RuntimeException e) {
                    // never lose a result: an unexpected failure must not leave the check running forever
                    LOG.log(Level.WARNING, "Unexpected error checking " + proxy, e);
                    markDead(proxy);
                }
                pending.add(proxy);
                if (flushScheduled.compareAndSet(false, true)) {
                    uiExecutor.execute(flush);
                }
            });
        }
        pool.shutdown();
        return true;
    }

    /**
     * Checks a proxy against the API server. The proxy is only considered alive if the request
     * through it returns a valid API response that includes the anonymity level; anything else
     * (timeouts, errors, unexpected pages served by the proxy) marks it dead.
     */
    static void testProxy(Proxy proxy, RequestAPI requestAPI) {
        try {
            RequestAPI.Result result = requestAPI.request(proxy);
            if (result.getResponse().anonymity == null) {
                throw new IOException("API response did not contain an anonymity level");
            }
            proxy.setProxyStatus(ProxyStatus.ALIVE)
                    .setProxyAnonymity(result.getResponse().anonymity)
                    .setCountry(result.getResponse().country)
                    .setResponseTime(result.getResponseTimeMillis() + " (ms)");
        } catch (IOException | IllegalArgumentException e) {
            markDead(proxy);
        }
    }

    private static void markDead(Proxy proxy) {
        proxy.setProxyStatus(ProxyStatus.DEAD).setProxyAnonymity(null);
    }
}
