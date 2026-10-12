package co.proxychecker.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import co.proxychecker.components.RequestAPI;
import co.proxychecker.components.UserSettings;
import co.proxychecker.components.entities.ProxyType;

class CliOptionsTest {

    private static CliOptions parse(String... args) throws CliOptions.UsageException {
        return CliOptions.parse(args);
    }

    @Test
    void detectsCliInvocation() {
        assertTrue(CliOptions.isCliInvocation(new String[] {"--cli", "-i", "x"}));
        assertTrue(CliOptions.isCliInvocation(new String[] {"-i", "x", "--cli"}));
        assertTrue(CliOptions.isCliInvocation(new String[] {"--help"}));
        assertTrue(CliOptions.isCliInvocation(new String[] {"-V"}));
        assertFalse(CliOptions.isCliInvocation(new String[0]));
        assertFalse(CliOptions.isCliInvocation(new String[] {"proxies.txt"}));
    }

    @Test
    void defaults() throws Exception {
        CliOptions options = parse("--cli", "-i", "proxies.txt");
        assertEquals(Arrays.asList("proxies.txt"), options.getInputs());
        assertNull(options.getOutput());
        assertEquals(CliOptions.Format.TXT, options.getFormat());
        assertEquals(ProxyType.HTTP, options.getType());
        assertEquals(UserSettings.DEFAULT_THREADS, options.getThreads());
        assertEquals(UserSettings.DEFAULT_TIMEOUT, options.getTimeout());
        assertEquals(RequestAPI.DEFAULT_API_URL, options.getApiUrl());
        assertFalse(options.isOnlyWorking());
        assertFalse(options.isQuiet());
        assertNull(options.getMyIp());
    }

    @Test
    void parsesAllOptions() throws Exception {
        CliOptions options = parse("--cli", "-i", "a.txt", "--input=b.txt", "c.txt", "-t", "socks5",
                "--threads", "200", "--timeout=7000", "-o", "out.csv", "--only-working", "-q",
                "--my-ip", "1.2.3.4", "--api-url", "http://judge.example/");
        assertEquals(Arrays.asList("a.txt", "b.txt", "c.txt"), options.getInputs());
        assertEquals(ProxyType.SOCKS5, options.getType());
        assertEquals(200, options.getThreads());
        assertEquals(7000, options.getTimeout());
        assertEquals("out.csv", options.getOutput());
        assertEquals(CliOptions.Format.CSV, options.getFormat()); // implied by .csv
        assertTrue(options.isOnlyWorking());
        assertTrue(options.isQuiet());
        assertEquals("1.2.3.4", options.getMyIp());
        assertEquals("http://judge.example/", options.getApiUrl());
    }

    @Test
    void explicitFormatWinsOverExtension() throws Exception {
        assertEquals(CliOptions.Format.TXT, parse("-i", "x", "-o", "out.csv", "--format", "txt").getFormat());
        assertEquals(CliOptions.Format.CSV, parse("-i", "x", "-f", "CSV").getFormat());
        assertEquals(ProxyType.SOCKS4, parse("-i", "x", "--type", "SOCKS4").getType());
        assertEquals(Arrays.asList("-"), parse("-i", "-").getInputs());
    }

    @Test
    void helpAndVersionNeedNoInput() throws Exception {
        assertTrue(parse("--help").isHelp());
        assertTrue(parse("--cli", "-V").isVersion());
    }

    @Test
    void verboseAndVersionRules() throws Exception {
        assertTrue(parse("-i", "x", "-v").isVerbose());
        assertTrue(parse("-i", "x", "--verbose").isVerbose());
        assertTrue(parse("--cli", "--version").isVersion());
        CliOptions.UsageException e = assertThrows(CliOptions.UsageException.class,
                () -> parse("--cli", "-i", "x", "-V"));
        assertTrue(e.getMessage().contains("did you mean -v/--verbose?"));
        assertThrows(CliOptions.UsageException.class, () -> parse("-i", "x", "-v", "-q"));
    }

    @Test
    void rejectsInvalidCommandLines() {
        assertThrows(CliOptions.UsageException.class, () -> parse("--cli"));
        assertThrows(CliOptions.UsageException.class, () -> parse("-i"));
        assertThrows(CliOptions.UsageException.class, () -> parse("-i", "x", "--bogus"));
        assertThrows(CliOptions.UsageException.class, () -> parse("-i", "x", "-t", "ftp"));
        assertThrows(CliOptions.UsageException.class, () -> parse("-i", "x", "--threads", "0"));
        assertThrows(CliOptions.UsageException.class, () -> parse("-i", "x", "--threads", "5000"));
        assertThrows(CliOptions.UsageException.class, () -> parse("-i", "x", "--threads", "many"));
        assertThrows(CliOptions.UsageException.class, () -> parse("-i", "x", "--timeout", "100"));
        assertThrows(CliOptions.UsageException.class, () -> parse("-i", "x", "--format", "xml"));
        assertThrows(CliOptions.UsageException.class, () -> parse("-i", "x", "--api-url", "https://x/"));
    }
}
