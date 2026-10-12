package co.proxychecker.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import co.proxychecker.components.RequestAPI;
import co.proxychecker.components.Settings;
import co.proxychecker.components.UserSettings;
import co.proxychecker.components.entities.Proxy;
import co.proxychecker.components.entities.ProxyAnonymity;
import co.proxychecker.components.entities.ProxyStatus;
import co.proxychecker.core.ProxyCheckService;
import co.proxychecker.core.ProxyExporter;
import co.proxychecker.core.ProxyListReader;

/**
 * Headless command line mode. Never touches JavaFX or the GUI configuration file.
 * Results go to standard output (or a file), progress and the summary to standard error.
 */
public final class Cli {

    public static final int EXIT_OK = 0;
    public static final int EXIT_NONE_WORKING = 1;
    public static final int EXIT_USAGE = 2;
    public static final int EXIT_IO = 3;

    private static final long PROGRESS_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(250);

    private final InputStream stdin;
    private final PrintStream stdout;
    private final PrintStream stderr;
    private final boolean interactive;

    Cli(InputStream stdin, PrintStream stdout, PrintStream stderr, boolean interactive) {
        this.stdin = stdin;
        this.stdout = stdout;
        this.stderr = stderr;
        this.interactive = interactive;
    }

    public static void main(String[] args) {
        // keep library warnings (e.g. about individual proxies) off the terminal
        Logger.getLogger("co.proxychecker").setLevel(Level.SEVERE);
        System.exit(new Cli(System.in, System.out, System.err, System.console() != null).run(args));
    }

    /**
     * Runs the command line and returns the process exit code.
     */
    public int run(String[] args) {
        CliOptions options;
        try {
            options = CliOptions.parse(args);
        } catch (CliOptions.UsageException e) {
            stderr.println("error: " + e.getMessage());
            stderr.println("Try '--help' for more information.");
            return EXIT_USAGE;
        }
        if (options.isHelp()) {
            stdout.print(CliOptions.USAGE);
            stdout.flush();
            return EXIT_OK;
        }
        if (options.isVersion()) {
            stdout.println(Settings.getApplicationName() + " " + Settings.getApplicationVersion());
            return EXIT_OK;
        }

        List<String> specs = new ArrayList<>();
        Set<String> known = new HashSet<>();
        for (String input : options.getInputs()) {
            try {
                specs.addAll(read(input, known));
            } catch (NoSuchFileException e) {
                stderr.println("error: input file not found: " + input);
                return EXIT_IO;
            } catch (IOException e) {
                stderr.println("error: unable to read " + input + ": " + e.getMessage());
                return EXIT_IO;
            }
        }
        if (specs.isEmpty()) {
            stderr.println("error: no valid proxies found in the input "
                    + "(expected host:port, user:pass@host:port or host:port:user:pass per line)");
            return EXIT_USAGE;
        }

        UserSettings settings = new UserSettings()
                .setThreads(options.getThreads())
                .setTimeout(options.getTimeout())
                .setProxyType(options.getType());
        RequestAPI api = new RequestAPI(settings, options.getApiUrl());
        String ip = options.getMyIp();
        if (ip == null) {
            try {
                ip = api.request(null).getResponse().ip;
            } catch (IOException e) {
                ip = null;
            }
            if (ip == null || ip.trim().isEmpty()) {
                stderr.println("error: unable to determine your public IP address from " + options.getApiUrl()
                        + " (check your connection or pass --my-ip)");
                return EXIT_IO;
            }
        }
        settings.setIp(ip.trim());

        List<Proxy> proxies = ProxyCheckService.parse(specs, options.getType());
        List<Proxy> results = runCheck(proxies, settings, api, options.isQuiet());

        // report in input order, regardless of the order checks finished in
        Map<Proxy, Integer> order = new IdentityHashMap<>();
        for (int i = 0; i < proxies.size(); i++) {
            order.put(proxies.get(i), i);
        }
        results.sort(Comparator.comparing(order::get));

        List<Proxy> output = new ArrayList<>();
        int working = 0;
        int[] byAnonymity = new int[ProxyAnonymity.values().length];
        for (Proxy proxy : results) {
            boolean alive = proxy.getProxyStatus() == ProxyStatus.ALIVE;
            if (alive) {
                working++;
                if (proxy.getProxyAnonymity() != null) {
                    byAnonymity[proxy.getProxyAnonymity().ordinal()]++;
                }
            }
            if (alive || !options.isOnlyWorking()) {
                output.add(proxy);
            }
        }

        try {
            write(output, options);
        } catch (IOException e) {
            stderr.println("error: unable to write " + options.getOutput() + ": " + e.getMessage());
            return EXIT_IO;
        }

        if (!options.isQuiet()) {
            stderr.printf("Checked %d proxies: %d working (%d elite, %d anonymous, %d transparent), %d dead.%n",
                    results.size(), working,
                    byAnonymity[ProxyAnonymity.ELITE.ordinal()],
                    byAnonymity[ProxyAnonymity.ANONYMOUS.ordinal()],
                    byAnonymity[ProxyAnonymity.TRANSPARENT.ordinal()],
                    results.size() - working);
            if (options.getOutput() != null) {
                stderr.println("Results written to " + options.getOutput());
            }
        }
        return working > 0 ? EXIT_OK : EXIT_NONE_WORKING;
    }

    private List<String> read(String input, Set<String> known) throws IOException {
        if (input.equals("-")) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(stdin, StandardCharsets.UTF_8));
            return ProxyListReader.readNewProxies(reader, known);
        }
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(input), StandardCharsets.UTF_8)) {
            return ProxyListReader.readNewProxies(reader, known);
        }
    }

    private List<Proxy> runCheck(List<Proxy> proxies, UserSettings settings, RequestAPI api, boolean quiet) {
        List<Proxy> results = new ArrayList<>(proxies.size());
        CountDownLatch done = new CountDownLatch(1);
        ExecutorService callbacks = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "proxy-checker-results");
            thread.setDaemon(true);
            return thread;
        });
        int total = proxies.size();
        long started = System.nanoTime();
        long[] lastProgress = {0};
        int[] working = {0};
        int[] lastDecile = {0};

        ProxyCheckService.check(proxies, settings.getThreads(), ProxyCheckService.apiTester(api), callbacks,
                batch -> {
                    results.addAll(batch);
                    for (Proxy proxy : batch) {
                        if (proxy.getProxyStatus() == ProxyStatus.ALIVE) {
                            working[0]++;
                        }
                    }
                    if (quiet) {
                        return;
                    }
                    int checked = results.size();
                    if (interactive) {
                        long now = System.nanoTime();
                        if (now - lastProgress[0] >= PROGRESS_INTERVAL_NANOS || checked == total) {
                            lastProgress[0] = now;
                            stderr.printf("\rChecked %d/%d (%d%%), %d working", checked, total,
                                    checked * 100 / total, working[0]);
                            stderr.flush();
                        }
                    } else {
                        int decile = checked * 10 / total;
                        if (decile > lastDecile[0]) {
                            lastDecile[0] = decile;
                            stderr.printf("Checked %d/%d (%d%%), %d working%n", checked, total,
                                    checked * 100 / total, working[0]);
                        }
                    }
                },
                done::countDown);

        try {
            done.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        callbacks.shutdown();
        if (!quiet) {
            if (interactive) {
                stderr.println();
            }
            stderr.printf("Finished in %.1f s%n", (System.nanoTime() - started) / 1e9);
        }
        // results is only modified on the callbacks thread; awaiting termination publishes it safely
        try {
            callbacks.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return results;
    }

    private void write(List<Proxy> proxies, CliOptions options) throws IOException {
        if (options.getOutput() == null) {
            Writer writer = new OutputStreamWriter(stdout, StandardCharsets.UTF_8);
            write(proxies, options.getFormat(), writer);
            writer.flush();
            return;
        }
        try (Writer writer = Files.newBufferedWriter(Paths.get(options.getOutput()), StandardCharsets.UTF_8)) {
            write(proxies, options.getFormat(), writer);
        }
    }

    private static void write(List<Proxy> proxies, CliOptions.Format format, Writer writer) throws IOException {
        if (format == CliOptions.Format.CSV) {
            ProxyExporter.writeCsv(proxies, writer);
        } else {
            ProxyExporter.writeText(proxies, writer);
        }
    }
}
