package co.proxychecker.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private int run(InputStream stdin, String... args) {
        Cli cli = new Cli(stdin, new PrintStream(stdout, true), new PrintStream(stderr, true), false);
        return cli.run(args);
    }

    private int run(String... args) {
        return run(new ByteArrayInputStream(new byte[0]), args);
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

        assertEquals(Cli.EXIT_OK, code, stderr.toString());
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
        String err = stderr.toString();
        assertTrue(err.contains("Checked 4 proxies: 2 working (1 elite, 1 anonymous, 0 transparent), 2 dead."), err);
        assertEquals("", stdout.toString());
    }

    @Test
    void onlyWorkingTextToStdoutFromStdin() {
        String list = "127.0.0.1:" + closedPort + "\n" + socks5Proxy.address() + "\n";
        int code = run(new ByteArrayInputStream(list.getBytes(StandardCharsets.UTF_8)),
                "--cli", "-i", "-", "-t", "socks5", "--only-working", "-q", "--my-ip", "9.9.9.9",
                "--timeout", "2000", "--api-url", apiUrl());
        assertEquals(Cli.EXIT_OK, code, stderr.toString());
        assertEquals(socks5Proxy.address() + "\n", stdout.toString());
        assertEquals("", stderr.toString()); // quiet
    }

    @Test
    void exitCodeOneWhenNothingWorks() throws IOException {
        Path in = input("127.0.0.1:" + closedPort);
        assertEquals(Cli.EXIT_NONE_WORKING, run("--cli", "-i", in.toString(), "--timeout", "1000",
                "--api-url", apiUrl()));
        assertEquals("127.0.0.1:" + closedPort + "\n", stdout.toString()); // dead proxies listed without -w
    }

    @Test
    void exitCodeTwoForUsageErrors() throws IOException {
        assertEquals(Cli.EXIT_USAGE, run("--cli", "--threads", "0", "-i", "x"));
        assertTrue(stderr.toString().contains("error: invalid value '0' for --threads"));
        assertEquals(Cli.EXIT_USAGE, run("--cli", "-i", input("nothing valid here").toString()));
    }

    @Test
    void exitCodeThreeForIoAndLookupErrors() throws IOException {
        assertEquals(Cli.EXIT_IO, run("--cli", "-i", dir.resolve("missing.txt").toString()));
        assertTrue(stderr.toString().contains("input file not found"));

        String deadApi = "http://127.0.0.1:" + closedPort + "/";
        assertEquals(Cli.EXIT_IO, run("--cli", "-i", input(workingProxy.address()).toString(),
                "--timeout", "1000", "--api-url", deadApi));
        assertTrue(stderr.toString().contains("unable to determine your public IP"));
    }

    @Test
    void helpAndVersion() {
        assertEquals(Cli.EXIT_OK, run("--help"));
        assertTrue(stdout.toString().startsWith("Usage: java -jar ProxyChecker.jar --cli"));
        stdout.reset();
        assertEquals(Cli.EXIT_OK, run("--cli", "--version"));
        assertTrue(stdout.toString().startsWith("Proxy Checker "));
    }
}
