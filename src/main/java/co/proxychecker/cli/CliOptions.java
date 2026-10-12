package co.proxychecker.cli;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import co.proxychecker.components.RequestAPI;
import co.proxychecker.components.UserSettings;
import co.proxychecker.components.entities.ProxyType;

/**
 * Parsed command line options.
 */
public final class CliOptions {

    /** Output format of the results. */
    public enum Format {
        TXT, CSV
    }

    /** Thrown for invalid command lines. */
    public static final class UsageException extends Exception {
        private static final long serialVersionUID = 1L;

        UsageException(String message) {
            super(message);
        }
    }

    public static final String USAGE = String.join("\n",
            "Usage: java -jar ProxyChecker.jar --cli -i <file> [options]",
            "",
            "Checks HTTP, SOCKS4 and SOCKS5 proxies from the command line (no GUI).",
            "Proxy lists contain one proxy per line: host:port, user:pass@host:port or host:port:user:pass.",
            "",
            "Options:",
            "  -i, --input <file>     proxy list to check, repeatable; '-' reads standard input.",
            "                         Files may also be given as plain arguments.",
            "  -o, --output <file>    write results to <file> instead of standard output",
            "  -f, --format <fmt>     txt (one proxy per line, as loaded) or csv (all details).",
            "                         Default: csv if the output file ends in .csv, otherwise txt",
            "  -t, --type <type>      proxy type: http, socks4 or socks5 (default: http)",
            "      --threads <n>      concurrent checks, " + UserSettings.MIN_THREADS + "-" + UserSettings.MAX_THREADS
                    + " (default: " + UserSettings.DEFAULT_THREADS + ")",
            "      --timeout <ms>     connect/read timeout in milliseconds, at least " + UserSettings.MIN_TIMEOUT
                    + " (default: " + UserSettings.DEFAULT_TIMEOUT + ")",
            "  -w, --only-working     only output working proxies",
            "      --my-ip <ip>       your public IP address (skips looking it up)",
            "      --api-url <url>    proxy judge API to use (default: " + RequestAPI.DEFAULT_API_URL + ")",
            "  -q, --quiet            no progress or summary on standard error",
            "  -h, --help             show this help and exit",
            "  -V, --version          show the version and exit",
            "",
            "Exit codes:",
            "  0  at least one working proxy was found",
            "  1  the check completed but no proxy works",
            "  2  invalid command line or no valid proxies in the input",
            "  3  an input/output file could not be read/written or the public IP lookup failed",
            "",
            "Example:",
            "  java -jar ProxyChecker.jar --cli -i proxies.txt -t socks5 --threads 200 --timeout 5000 \\",
            "       -o results.csv --only-working",
            "");

    private final List<String> inputs = new ArrayList<>();
    private String output;
    private Format format;
    private ProxyType type = ProxyType.HTTP;
    private int threads = UserSettings.DEFAULT_THREADS;
    private int timeout = UserSettings.DEFAULT_TIMEOUT;
    private boolean onlyWorking;
    private boolean quiet;
    private String myIp;
    private String apiUrl = RequestAPI.DEFAULT_API_URL;
    private boolean help;
    private boolean version;

    private CliOptions() {
    }

    /**
     * @return whether the arguments ask for command line mode rather than the GUI
     */
    public static boolean isCliInvocation(String[] args) {
        for (String arg : args) {
            if (arg.equals("--cli")) {
                return true;
            }
        }
        return args.length == 1 && (args[0].equals("-h") || args[0].equals("--help")
                || args[0].equals("-V") || args[0].equals("--version"));
    }

    public static CliOptions parse(String[] args) throws UsageException {
        CliOptions options = new CliOptions();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            String value = null;
            if (arg.startsWith("--") && arg.contains("=")) {
                value = arg.substring(arg.indexOf('=') + 1);
                arg = arg.substring(0, arg.indexOf('='));
            }
            switch (arg) {
                case "--cli":
                    break;
                case "-h":
                case "--help":
                    options.help = true;
                    break;
                case "-V":
                case "--version":
                    options.version = true;
                    break;
                case "-w":
                case "--only-working":
                    options.onlyWorking = true;
                    break;
                case "-q":
                case "--quiet":
                    options.quiet = true;
                    break;
                case "-i":
                case "--input":
                    options.inputs.add(value != null ? value : next(args, ++i, arg));
                    break;
                case "-o":
                case "--output":
                    options.output = value != null ? value : next(args, ++i, arg);
                    break;
                case "-f":
                case "--format":
                    options.format = parseFormat(value != null ? value : next(args, ++i, arg));
                    break;
                case "-t":
                case "--type":
                    String type = value != null ? value : next(args, ++i, arg);
                    try {
                        options.type = ProxyType.fromString(type);
                    } catch (IllegalArgumentException e) {
                        throw new UsageException("invalid proxy type '" + type + "' (expected http, socks4 or socks5)");
                    }
                    break;
                case "--threads":
                    options.threads = parseInt(value != null ? value : next(args, ++i, arg), arg,
                            UserSettings.MIN_THREADS, UserSettings.MAX_THREADS);
                    break;
                case "--timeout":
                    options.timeout = parseInt(value != null ? value : next(args, ++i, arg), arg,
                            UserSettings.MIN_TIMEOUT, Integer.MAX_VALUE);
                    break;
                case "--my-ip":
                    options.myIp = value != null ? value : next(args, ++i, arg);
                    break;
                case "--api-url":
                    options.apiUrl = value != null ? value : next(args, ++i, arg);
                    if (!options.apiUrl.toLowerCase(Locale.ROOT).startsWith("http://")) {
                        throw new UsageException("--api-url must be an http:// URL");
                    }
                    break;
                default:
                    if (arg.startsWith("-") && !arg.equals("-")) {
                        throw new UsageException("unknown option '" + arg + "'");
                    }
                    options.inputs.add(arg);
            }
        }
        if (!options.help && !options.version && options.inputs.isEmpty()) {
            throw new UsageException("no input given (use -i <file>, or -i - for standard input)");
        }
        return options;
    }

    private static String next(String[] args, int index, String option) throws UsageException {
        if (index >= args.length) {
            throw new UsageException("option '" + option + "' requires a value");
        }
        return args[index];
    }

    private static int parseInt(String text, String option, int min, int max) throws UsageException {
        try {
            int value = Integer.parseInt(text.trim());
            if (value < min || value > max) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException e) {
            throw new UsageException("invalid value '" + text + "' for " + option + " (expected "
                    + (max == Integer.MAX_VALUE ? "at least " + min : min + "-" + max) + ")");
        }
    }

    private static Format parseFormat(String text) throws UsageException {
        switch (text.trim().toLowerCase(Locale.ROOT)) {
            case "txt":
            case "text":
                return Format.TXT;
            case "csv":
                return Format.CSV;
            default:
                throw new UsageException("invalid format '" + text + "' (expected txt or csv)");
        }
    }

    public List<String> getInputs() {
        return Collections.unmodifiableList(inputs);
    }

    /** @return the output file, or null for standard output */
    public String getOutput() {
        return output;
    }

    /** @return the explicit format or the one implied by the output file name */
    public Format getFormat() {
        if (format != null) {
            return format;
        }
        return output != null && output.toLowerCase(Locale.ROOT).endsWith(".csv") ? Format.CSV : Format.TXT;
    }

    public ProxyType getType() {
        return type;
    }

    public int getThreads() {
        return threads;
    }

    public int getTimeout() {
        return timeout;
    }

    public boolean isOnlyWorking() {
        return onlyWorking;
    }

    public boolean isQuiet() {
        return quiet;
    }

    /** @return the public IP given on the command line, or null to look it up */
    public String getMyIp() {
        return myIp;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public boolean isHelp() {
        return help;
    }

    public boolean isVersion() {
        return version;
    }
}
