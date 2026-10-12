package co.proxychecker.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import co.proxychecker.components.net.ProxyHttpClient;
import co.proxychecker.core.ProxyExporter;
import co.proxychecker.support.FakeServer;

/**
 * End-to-end runs of the command line against local fake judge API and proxy servers.
 */
class CliTest {

    @TempDir
    Path dir;

    private FakeServer api;
    private FakeServer workingProxy;
    private FakeServer authProxy;
    private FakeServer socks5Proxy;
    private int closedPort;

    private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    @BeforeEach
    void setUp() throws IOException {
        // the judge API: direct requests return the caller's public IP
        api = new FakeServer((in, out) -> {
            FakeServer.readHttpRequest(in);
            FakeServer.writeHttp(out, "200 OK", "{\"ip\":\"9.9.9.9\"}");
        });
        // a working HTTP proxy (answers like the judge would through it, but only for our IP)
        workingProxy = new FakeServer((in, out) -> {
            String request = FakeServer.readHttpRequest(in);
            if (request.startsWith("GET http://127.0.0.1:" + api.getPort() + "/?ip=9.9.9.9 HTTP/1.1")) {
                FakeServer.writeHttp(out, "200 OK", "{\"anonymity\":\"ANONYMOUS\",\"country\":\"Korea, Republic of\"}");
            } else {
                FakeServer.writeHttp(out, "400 Bad Request", "");
            }
        });
        String auth = "Proxy-Authorization: " + ProxyHttpClient.basicAuth("u", "p") + "\r\n";
        authProxy = new FakeServer((in, out) -> {
            if (FakeServer.readHttpRequest(in).contains(auth)) {
                FakeServer.writeHttp(out, "200 OK", FakeServer.JUDGE_RESPONSE);
            } else {
                FakeServer.writeHttp(out, "407 Proxy Authentication Required", "");
            }
        });
        socks5Proxy = new FakeServer((in, out) -> {
            byte[] greeting = FakeServer.read(in, 2);
            FakeServer.read(in, greeting[1]);
            out.write(new byte[] {5, 0});
            out.flush();
            byte[] request = FakeServer.read(in, 4);
            FakeServer.read(in, request[3] == 1 ? 4 + 2 : (FakeServer.read(in, 1)[0] & 0xFF) + 2);
            out.write(new byte[] {5, 0, 0, 1, 127, 0, 0, 1, 0, 80});
            FakeServer.readHttpRequest(in);
            FakeServer.writeHttp(out, "200 OK", FakeServer.JUDGE_RESPONSE);
        });
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
    }

    @AfterEach
    void tearDown() throws IOException {
        api.close();
        workingProxy.close();
        authProxy.close();
        socks5Proxy.close();
    }

    private boolean interactive = false;
    private long progressInterval = 60_000;

    private int run(InputStream stdin, String... args) {
        Cli cli = new Cli(stdin, new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8), interactive, progressInterval);
        return cli.run(args);
    }

    private int run(String... args) {
        return run(new ByteArrayInputStream(new byte[0]), args);
    }

    /**
     * Standard error as text, with the platform line separator (CRLF on Windows, used by println)
     * normalised to "\n" so assertions read the same on every OS. Any other '\r' is kept.
     */
    private String err() {
        return normalize(stderr);
    }

    private String out() {
        return normalize(stdout);
    }

    private static String normalize(ByteArrayOutputStream stream) {
        return new String(stream.toByteArray(), StandardCharsets.UTF_8).replace(System.lineSeparator(), "\n");
    }

    private String apiUrl() {
        return "http://127.0.0.1:" + api.getPort() + "/";
    }

    private Path input(String... lines) throws IOException {
        Path file = dir.resolve("proxies.txt");
        Files.write(file, Arrays.asList(lines), StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void checksHttpProxiesAndWritesCsvInInputOrder() throws IOException {
        Path in = input(
                "127.0.0.1:" + closedPort,
                workingProxy.address(),
                "garbage line",
                "u:p@" + authProxy.address(),
                "u:wrong@" + authProxy.address(),
                workingProxy.address()); // duplicate
        Path out = dir.resolve("results.csv");

        int code = run("--cli", "-i", in.toString(), "-o", out.toString(), "--timeout", "2000",
                "--threads", "4", "--api-url", apiUrl());

        assertEquals(Cli.EXIT_OK, code, err());
        List<String> lines = Files.readAllLines(out, StandardCharsets.UTF_8);
        assertEquals(Arrays.asList(
                ProxyExporter.CSV_HEADER,
                "127.0.0.1," + closedPort + ",,,HTTP,DEAD,,,",
                "127.0.0.1," + workingProxy.getPort() + ",,,HTTP,ALIVE,ANONYMOUS,\"Korea, Republic of\","
                        + lines.get(2).substring(lines.get(2).lastIndexOf(',') + 1),
                lines.get(3),
                "127.0.0.1," + authProxy.getPort() + ",u,wrong,HTTP,DEAD,,,"), lines);
        assertTrue(lines.get(3).startsWith("127.0.0.1," + authProxy.getPort() + ",u,p,HTTP,ALIVE,ELITE,Canada,"));
        assertTrue(lines.get(2).endsWith(" (ms)"));
        String err = err();
        assertTrue(err.contains("Checking 4 http proxies with 4 threads (timeout 2000 ms, public IP 9.9.9.9)"), err);
        assertTrue(err.contains(": 4 checked, 2 working (1 elite, 1 anonymous, 0 transparent), 2 dead"), err);
        assertTrue(err.contains("Dead: 1 connection refused, 1 auth failed (HTTP 407)")
                || err.contains("Dead: 1 auth failed (HTTP 407), 1 connection refused"), err);
        assertTrue(err.contains("Wrote 4 proxies to " + out + " (csv)"), err);
        assertTrue(!err.contains("[WORKING]") && !err.contains("[DEAD]"), "not verbose by default: " + err);
        assertEquals("", out());
    }

    @Test
    void onlyWorkingTextToStdoutFromStdin() {
        String list = "127.0.0.1:" + closedPort + "\n" + socks5Proxy.address() + "\n";
        int code = run(new ByteArrayInputStream(list.getBytes(StandardCharsets.UTF_8)),
                "--cli", "-i", "-", "-t", "socks5", "--only-working", "-q", "--my-ip", "9.9.9.9",
                "--timeout", "2000", "--api-url", apiUrl());
        assertEquals(Cli.EXIT_OK, code, err());
        assertEquals(socks5Proxy.address() + "\n", out());
        assertEquals("", err()); // quiet
    }

    @Test
    void exitCodeOneWhenNothingWorks() throws IOException {
        Path in = input("127.0.0.1:" + closedPort);
        assertEquals(Cli.EXIT_NONE_WORKING, run("--cli", "-i", in.toString(), "--timeout", "1000",
                "--api-url", apiUrl()));
        assertEquals("127.0.0.1:" + closedPort + "\n", out()); // dead proxies listed without -w
    }

    @Test
    void exitCodeTwoForUsageErrors() throws IOException {
        assertEquals(Cli.EXIT_USAGE, run("--cli", "--threads", "0", "-i", "x"));
        assertTrue(err().contains("error: invalid value '0' for --threads"));
        assertEquals(Cli.EXIT_USAGE, run("--cli", "-i", input("nothing valid here").toString()));
    }

    @Test
    void exitCodeThreeForIoAndLookupErrors() throws IOException {
        assertEquals(Cli.EXIT_IO, run("--cli", "-i", dir.resolve("missing.txt").toString()));
        assertTrue(err().contains("input file not found"));

        String deadApi = "http://127.0.0.1:" + closedPort + "/";
        assertEquals(Cli.EXIT_IO, run("--cli", "-i", input(workingProxy.address()).toString(),
                "--timeout", "1000", "--api-url", deadApi));
        assertTrue(err().contains("unable to determine your public IP"));
    }

    @Test
    void verbosePrintsEachResultWithReasonAndSettings() throws IOException {
        try (FakeServer slow = new FakeServer((in, out) -> {
                FakeServer.readHttpRequest(in);
                sleep(3000);
            });
             FakeServer notJudge = new FakeServer((in, out) -> {
                FakeServer.readHttpRequest(in);
                FakeServer.writeHttp(out, "200 OK", "<html>captive portal</html>");
            })) {
            Path in = input(
                    "# comment lines are ignored",
                    workingProxy.address(),
                    "127.0.0.1:" + closedPort,
                    "u:wrong@" + authProxy.address(),
                    slow.address(),
                    notJudge.address(),
                    "bad entry",
                    workingProxy.address());
            int code = run("--cli", "-i", in.toString(), "-v", "--timeout", "500", "--api-url", apiUrl());
            assertEquals(Cli.EXIT_OK, code);
            String err = err();
            System.out.println("---- sample verbose stderr ----\n" + err + "-------------------------------");
            assertTrue(err.contains("Input:      " + in + ": 5 valid, 1 invalid, 1 duplicate"), err);
            assertTrue(err.contains("Public IP:  9.9.9.9 (looked up via " + apiUrl() + ")"), err);
            assertTrue(err.contains("Proxy type: http"), err);
            assertTrue(err.contains("Threads:    100"), err);
            assertTrue(err.contains("Timeout:    500 ms"), err);
            assertTrue(err.contains("Output:     standard output (txt)"), err);
            assertTrue(err.matches("(?s).*\\[WORKING\\] " + workingProxy.address()
                    + " http anonymous Korea, Republic of \\d+ms\n.*"), err);
            assertTrue(err.contains("[DEAD] 127.0.0.1:" + closedPort + " http connection refused\n"), err);
            assertTrue(err.contains("[DEAD] u@" + authProxy.address() + " http auth failed (HTTP 407)\n"), err);
            assertTrue(!err.contains("wrong"), "password must not be printed");
            assertTrue(err.contains("[DEAD] " + slow.address() + " http timeout\n"), err);
            assertTrue(err.contains("[DEAD] " + notJudge.address() + " http bad API response\n"), err);
        }
    }

    @Test
    void verboseShowsSocksHandshakeAndAuthFailures() throws IOException {
        try (FakeServer garbage = new FakeServer((in, out) -> {
                FakeServer.read(in, 3);
                out.write("HTTP/1.1 400 Bad Request\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
            });
             FakeServer authOnly = new FakeServer((in, out) -> {
                byte[] greeting = FakeServer.read(in, 2);
                FakeServer.read(in, greeting[1]);
                out.write(new byte[] {5, 2});
                out.flush();
                FakeServer.read(in, 1);
                FakeServer.read(in, FakeServer.read(in, 1)[0] & 0xFF);
                FakeServer.read(in, FakeServer.read(in, 1)[0] & 0xFF);
                out.write(new byte[] {1, 1});
            })) {
            Path in = input(garbage.address(), "eve:x@" + authOnly.address(), authOnly.address());
            assertEquals(Cli.EXIT_NONE_WORKING, run("--cli", "-i", in.toString(), "-t", "socks5", "--verbose",
                    "--my-ip", "9.9.9.9", "--api-url", apiUrl()));
            String err = err();
            assertTrue(err.contains("Public IP:  9.9.9.9 (from --my-ip)"), err);
            assertTrue(err.contains("[DEAD] " + garbage.address() + " socks5 bad handshake\n"), err);
            assertTrue(err.contains("[DEAD] eve@" + authOnly.address() + " socks5 auth failed\n"), err);
            assertTrue(err.contains("[DEAD] " + authOnly.address() + " socks5 auth required\n"), err);
            assertTrue(err.contains("Dead: "), err);
        }
    }

    @Test
    void plainProgressLinesWhenNotATerminal() throws IOException {
        progressInterval = 200;
        try (FakeServer slow = new FakeServer((in, out) -> {
            FakeServer.readHttpRequest(in);
            sleep(1500);
            FakeServer.writeHttp(out, "200 OK", FakeServer.JUDGE_RESPONSE);
        })) {
            run("--cli", "-i", input(slow.address(), workingProxy.address()).toString(), "--timeout", "3000",
                    "--my-ip", "9.9.9.9", "--api-url", apiUrl());
            String err = err();
            assertTrue(err.contains("Progress: 1/2 checked (50%), 1 working, "), err);
            assertTrue(!err.contains("\r"), "no carriage returns for non-terminals: " + err);
        }
    }

    @Test
    void liveProgressLineOnATerminal() throws IOException {
        interactive = true;
        progressInterval = 50;
        run("--cli", "-i", input(workingProxy.address()).toString(), "--my-ip", "9.9.9.9", "--api-url", apiUrl());
        String err = err();
        assertTrue(err.contains("\rChecking: 1/1 checked (100%), 1 working, "), err);
        assertTrue(!err.contains("Progress: "), err);
    }

    @Test
    void versionCombinedWithOtherOptionsIsAnError() throws IOException {
        assertEquals(Cli.EXIT_USAGE, run("--cli", "-i", input(workingProxy.address()).toString(), "-t", "socks4",
                "-V", "-o", "results.csv"));
        assertTrue(err().contains("did you mean -v/--verbose?"), err());
        assertEquals("", out());
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void helpAndVersion() {
        assertEquals(Cli.EXIT_OK, run("--help"));
        assertTrue(out().startsWith("Usage: java -jar ProxyChecker.jar --cli"));
        stdout.reset();
        assertEquals(Cli.EXIT_OK, run("--cli", "--version"));
        assertTrue(out().startsWith("Proxy Checker "));
    }

    @Test
    void ctrlCStopsWritesPartialResultsAndExits130() throws Exception {
        CountDownLatch slowConnected = new CountDownLatch(1);
        try (FakeServer slow = new FakeServer((in, out) -> {
            slowConnected.countDown();
            while (in.read() != -1) {
                // never answer
            }
        })) {
            Path in = input(workingProxy.address(), slow.address());
            Path out = dir.resolve("partial.csv");
            Cli cli = new Cli(new ByteArrayInputStream(new byte[0]), new PrintStream(stdout, true, StandardCharsets.UTF_8),
                    new PrintStream(stderr, true, StandardCharsets.UTF_8), false, 60_000);
            AtomicInteger code = new AtomicInteger(-1);
            Thread runner = new Thread(() -> code.set(cli.run(new String[] {"-i", in.toString(), "--api-url", apiUrl(),
                    "--timeout", "60000", "-o", out.toString(), "-v"})));
            runner.start();
            assertTrue(slowConnected.await(10, TimeUnit.SECONDS));
            long deadline = System.currentTimeMillis() + 10_000;
            while (!err().contains("[WORKING]") && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            long start = System.nanoTime();
            cli.requestStop(); // what the Ctrl+C shutdown hook does
            runner.join(5000);
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            assertFalse(runner.isAlive());
            assertEquals(Cli.EXIT_INTERRUPTED, code.get());
            assertTrue(elapsed < 3000, "took " + elapsed + " ms");
            String err = err();
            assertTrue(err.contains("Stopped after "), err);
            assertTrue(err.contains("1 of 2 checked, 1 working"), err);
            assertTrue(err.contains("Not checked: 1 (interrupted)"), err);
            assertTrue(err.contains("Wrote 1 proxy to " + out), err);
            List<String> lines = Files.readAllLines(out, StandardCharsets.UTF_8);
            assertEquals(2, lines.size(), lines.toString()); // header + the working proxy
            assertTrue(lines.get(1).startsWith("127.0.0.1," + workingProxy.getPort() + ","), lines.toString());
        }
    }

    @Test
    void ctrlCBeforeCheckingStartsExits130() throws IOException {
        Path in = input(workingProxy.address());
        Cli cli = new Cli(new ByteArrayInputStream(new byte[0]), new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8), false, 60_000);
        cli.requestStop();
        assertEquals(Cli.EXIT_INTERRUPTED, cli.run(new String[] {"-i", in.toString(), "--api-url", apiUrl()}));
        assertTrue(err().contains("Interrupted before checking started"));
    }
}
