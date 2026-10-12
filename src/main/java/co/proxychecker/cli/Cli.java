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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

    /** Live line refresh on a terminal. */
    static final long INTERACTIVE_INTERVAL_MILLIS = 250;
    /** Plain progress lines when standard error isn't a terminal (pipes, log files, Docker without -t). */
    static final long PLAIN_INTERVAL_MILLIS = 5000;

    private final InputStream stdin;
    private final PrintStream stdout;
    private final PrintStream stderr;
    private final boolean interactive;
    private final long progressIntervalMillis;

    Cli(InputStream stdin, PrintStream stdout, PrintStream stderr, boolean interactive, long progressIntervalMillis) {
        this.stdin = stdin;
        this.stdout = stdout;
        this.stderr = stderr;
        this.interactive = interactive;
        this.progressIntervalMillis = progressIntervalMillis;
    }

    public static void main(String[] args) {
        // keep library warnings (e.g. about individual proxies) off the terminal
        Logger.getLogger("co.proxychecker").setLevel(Level.SEVERE);
        boolean terminal = isTerminal();
        System.exit(new Cli(System.in, System.out, System.err, terminal,
                terminal ? INTERACTIVE_INTERVAL_MILLIS : PLAIN_INTERVAL_MILLIS).run(args));
    }

    /**
     * Whether we are attached to a terminal. {@code System.console()} is null when input/output is
     * redirected (e.g. Docker without -t); on Java 22+ it may exist anyway, so also ask
     * {@code Console.isTerminal()} when available.
     */
    static boolean isTerminal() {
        java.io.Console console = System.console();
        if (console == null) {
            return false;
        }
        try {
            return (Boolean) java.io.Console.class.getMethod("isTerminal").invoke(console);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return true; // Java < 22: a console exists only for terminals
        }
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
        List<String> inputSummaries = new ArrayList<>();
        for (String input : options.getInputs()) {
            try {
                InputStats stats = read(input, known, specs);
                inputSummaries.add((input.equals("-") ? "standard input" : input) + ": " + stats);
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
        String ipSource = "from --my-ip";
        if (ip == null) {
            ipSource = "looked up via " + options.getApiUrl();
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

        String type = options.getType().name().toLowerCase(Locale.ROOT);
        String destination = (options.getOutput() == null ? "standard output" : options.getOutput())
                + " (" + options.getFormat().name().toLowerCase(Locale.ROOT)
                + (options.isOnlyWorking() ? ", working proxies only" : "") + ")";
        if (options.isVerbose()) {
            stderr.println(Settings.getApplicationName() + " " + Settings.getApplicationVersion());
            for (String summary : inputSummaries) {
                stderr.println("Input:      " + summary);
            }
            stderr.println("Public IP:  " + settings.getIp() + " (" + ipSource + ")");
            stderr.println("Judge API:  " + options.getApiUrl());
            stderr.println("Proxy type: " + type);
            stderr.println("Threads:    " + options.getThreads());
            stderr.println("Timeout:    " + options.getTimeout() + " ms");
            stderr.println("Output:     " + destination);
        }
        if (!options.isQuiet()) {
            stderr.printf(Locale.ROOT, "Checking %d %s proxies with %d threads (timeout %d ms, public IP %s)%n",
                    specs.size(), type, Math.min(options.getThreads(), specs.size()), options.getTimeout(),
                    settings.getIp());
        }

        List<Proxy> proxies = ProxyCheckService.parse(specs, options.getType());
        ProgressReporter progress = options.isQuiet() ? null
                : new ProgressReporter(stderr, interactive, options.isVerbose(), proxies.size(), progressIntervalMillis);
        List<Proxy> results = runCheck(proxies, settings, api, progress);
        if (progress != null) {
            progress.finish();
        }

        // report in input order, regardless of the order checks finished in
        Map<Proxy, Integer> order = new IdentityHashMap<>();
        for (int i = 0; i < proxies.size(); i++) {
            order.put(proxies.get(i), i);
        }
        results.sort(Comparator.comparing(order::get));

        List<Proxy> output = new ArrayList<>();
        int working = 0;
        int[] byAnonymity = new int[ProxyAnonymity.values().length];
        Map<String, Integer> deadReasons = new LinkedHashMap<>();
        for (Proxy proxy : results) {
            boolean alive = proxy.getProxyStatus() == ProxyStatus.ALIVE;
            if (alive) {
                working++;
                if (proxy.getProxyAnonymity() != null) {
                    byAnonymity[proxy.getProxyAnonymity().ordinal()]++;
                }
            } else {
                String reason = proxy.getFailureReason() == null ? "failed" : proxy.getFailureReason();
                deadReasons.merge(reason, 1, Integer::sum);
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

        if (progress != null) {
            stderr.printf(Locale.ROOT, "Done in %.1fs: %d checked, %d working (%d elite, %d anonymous, %d transparent),"
                            + " %d dead%n",
                    progress.elapsedSeconds(), results.size(), working,
                    byAnonymity[ProxyAnonymity.ELITE.ordinal()],
                    byAnonymity[ProxyAnonymity.ANONYMOUS.ordinal()],
                    byAnonymity[ProxyAnonymity.TRANSPARENT.ordinal()],
                    results.size() - working);
            if (!deadReasons.isEmpty()) {
                List<Map.Entry<String, Integer>> reasons = new ArrayList<>(deadReasons.entrySet());
                reasons.sort((a, b) -> b.getValue() - a.getValue());
                StringBuilder line = new StringBuilder("Dead: ");
                for (int i = 0; i < reasons.size(); i++) {
                    line.append(i == 0 ? "" : ", ").append(reasons.get(i).getValue()).append(' ')
                            .append(reasons.get(i).getKey());
                }
                stderr.println(line);
            }
            stderr.println("Wrote " + output.size() + " " + (output.size() == 1 ? "proxy" : "proxies")
                    + " to " + destination);
        }
        return working > 0 ? EXIT_OK : EXIT_NONE_WORKING;
    }

    /** Counts of what was read from one input. */
    private static final class InputStats {
        int valid;
        int invalid;
        int duplicates;

        @Override
        public String toString() {
            return valid + " valid, " + invalid + " invalid, " + duplicates + " duplicate";
        }
    }

    private InputStats read(String input, Set<String> known, List<String> specs) throws IOException {
        if (input.equals("-")) {
            return read(new BufferedReader(new InputStreamReader(stdin, StandardCharsets.UTF_8)), known, specs);
        }
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(input), StandardCharsets.UTF_8)) {
            return read(reader, known, specs);
        }
    }

    private static InputStats read(BufferedReader reader, Set<String> known, List<String> specs) throws IOException {
        InputStats stats = new InputStats();
        String line;
        while ((line = reader.readLine()) != null) {
            String value = ProxyListReader.normalize(line);
            if (value.isEmpty() || value.startsWith("#")) {
                continue;
            }
            if (!Proxy.isValidFormat(value)) {
                stats.invalid++;
            } else if (known.add(value)) {
                stats.valid++;
                specs.add(value);
            } else {
                stats.duplicates++;
            }
        }
        return stats;
    }

    private static List<Proxy> runCheck(List<Proxy> proxies, UserSettings settings, RequestAPI api,
                                        ProgressReporter progress) {
        List<Proxy> results = new ArrayList<>(proxies.size());
        CountDownLatch done = new CountDownLatch(1);
        ExecutorService callbacks = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "proxy-checker-results");
            thread.setDaemon(true);
            return thread;
        });
        ProxyCheckService.check(proxies, settings.getThreads(), ProxyCheckService.apiTester(api), callbacks,
                batch -> {
                    results.addAll(batch);
                    if (progress != null) {
                        for (Proxy proxy : batch) {
                            progress.onChecked(proxy);
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
