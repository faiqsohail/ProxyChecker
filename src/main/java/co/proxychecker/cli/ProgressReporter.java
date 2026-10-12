package co.proxychecker.cli;

import java.io.PrintStream;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import co.proxychecker.components.entities.Proxy;
import co.proxychecker.components.entities.ProxyStatus;

/**
 * Writes CLI progress to standard error. On a terminal a single progress line is redrawn in place;
 * otherwise (pipes, log files, Docker without -t) plain progress lines are printed periodically.
 * Verbose mode additionally prints one line per checked proxy as soon as it is reported.
 * All methods are thread-safe.
 */
final class ProgressReporter {

    private final PrintStream err;
    private final boolean interactive;
    private final boolean verbose;
    private final int total;
    private final long startNanos = System.nanoTime();
    private final ScheduledExecutorService ticker;

    private int checked;
    private int working;
    private int liveLineLength;
    private boolean finished;

    ProgressReporter(PrintStream err, boolean interactive, boolean verbose, int total, long intervalMillis) {
        this.err = err;
        this.interactive = interactive;
        this.verbose = verbose;
        this.total = total;
        this.ticker = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "proxy-checker-progress");
            thread.setDaemon(true);
            return thread;
        });
        // a timer (rather than only result callbacks) keeps progress visible while every worker is
        // waiting on slow proxies
        ticker.scheduleAtFixedRate(this::tick, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    synchronized void onChecked(Proxy proxy) {
        checked++;
        if (proxy.getProxyStatus() == ProxyStatus.ALIVE) {
            working++;
        }
        if (verbose) {
            clearLiveLine();
            err.println(describe(proxy));
        }
        if (interactive) {
            drawLiveLine();
        }
    }

    synchronized void finish() {
        finished = true;
        ticker.shutdownNow();
        if (interactive) {
            drawLiveLine();
            err.println();
            liveLineLength = 0;
        }
        err.flush();
    }

    private synchronized void tick() {
        if (finished) {
            return;
        }
        if (interactive) {
            drawLiveLine();
        } else {
            err.println("Progress: " + status() + " elapsed");
            err.flush();
        }
    }

    private String status() {
        return String.format(Locale.ROOT, "%d/%d checked (%d%%), %d working, %.1fs",
                checked, total, total == 0 ? 100 : checked * 100 / total, working, elapsedSeconds());
    }

    double elapsedSeconds() {
        return (System.nanoTime() - startNanos) / 1e9;
    }

    private void drawLiveLine() {
        String line = "Checking: " + status();
        StringBuilder out = new StringBuilder("\r").append(line);
        for (int i = line.length(); i < liveLineLength; i++) {
            out.append(' ');
        }
        liveLineLength = line.length();
        err.print(out);
        err.flush();
    }

    private void clearLiveLine() {
        if (interactive && liveLineLength > 0) {
            StringBuilder out = new StringBuilder("\r");
            for (int i = 0; i < liveLineLength; i++) {
                out.append(' ');
            }
            err.print(out.append('\r'));
            liveLineLength = 0;
        }
    }

    /**
     * One line per proxy, e.g. {@code [WORKING] 1.0.133.89:4153 socks4 anonymous Canada 812ms} or
     * {@code [DEAD] 1.2.3.4:1080 socks5 connection refused}. Passwords are never printed.
     */
    static String describe(Proxy proxy) {
        StringBuilder line = new StringBuilder();
        boolean alive = proxy.getProxyStatus() == ProxyStatus.ALIVE;
        line.append(alive ? "[WORKING] " : "[DEAD] ");
        if (proxy.hasCredentials()) {
            line.append(proxy.getUsername()).append('@');
        }
        line.append(proxy.getIp()).append(':').append(proxy.getPort());
        line.append(' ').append(proxy.getProxyType().name().toLowerCase(Locale.ROOT));
        if (alive) {
            if (proxy.getProxyAnonymity() != null) {
                line.append(' ').append(proxy.getProxyAnonymity().name().toLowerCase(Locale.ROOT));
            }
            if (proxy.getCountry() != null && !proxy.getCountry().isEmpty()) {
                line.append(' ').append(proxy.getCountry());
            }
            if (proxy.getResponseTimeMillis() >= 0) {
                line.append(' ').append(proxy.getResponseTimeMillis()).append("ms");
            }
        } else {
            line.append(' ').append(proxy.getFailureReason() == null ? "failed" : proxy.getFailureReason());
        }
        return line.toString();
    }
}
