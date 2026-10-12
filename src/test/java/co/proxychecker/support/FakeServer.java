package co.proxychecker.support;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Local TCP server for tests that handles every connection with the given handler.
 */
public final class FakeServer implements AutoCloseable {

    /** Handles one accepted connection. */
    @FunctionalInterface
    public interface Handler {
        void handle(InputStream in, OutputStream out) throws IOException;
    }

    private final ServerSocket server;
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "fake-server");
        t.setDaemon(true);
        return t;
    });

    public FakeServer(Handler handler) throws IOException {
        server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        pool.execute(() -> {
            while (!server.isClosed()) {
                try {
                    Socket client = server.accept();
                    pool.execute(() -> {
                        try (Socket socket = client) {
                            socket.setSoTimeout(10_000);
                            handler.handle(socket.getInputStream(), socket.getOutputStream());
                            socket.getOutputStream().flush();
                        } catch (IOException ignored) {
                            // client went away
                        }
                    });
                } catch (IOException e) {
                    return;
                }
            }
        });
    }

    public int getPort() {
        return server.getLocalPort();
    }

    /** host:port of this server. */
    public String address() {
        return "127.0.0.1:" + getPort();
    }

    @Override
    public void close() throws IOException {
        server.close();
        pool.shutdownNow();
    }

    // ---- helpers for handlers ----

    public static byte[] read(InputStream in, int length) throws IOException {
        byte[] buffer = new byte[length];
        int offset = 0;
        while (offset < length) {
            int r = in.read(buffer, offset, length - offset);
            if (r < 0) {
                throw new EOFException();
            }
            offset += r;
        }
        return buffer;
    }

    /** Reads a NUL terminated string. */
    public static String readNulString(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) > 0) {
            out.write(b);
        }
        if (b < 0) {
            throw new EOFException();
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    /** Reads HTTP request headers (up to the blank line) and returns them. */
    public static String readHttpRequest(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int matched = 0;
        byte[] terminator = {'\r', '\n', '\r', '\n'};
        int b;
        while (matched < 4 && (b = in.read()) != -1) {
            out.write(b);
            matched = (b == terminator[matched]) ? matched + 1 : (b == '\r' ? 1 : 0);
        }
        return new String(out.toByteArray(), StandardCharsets.ISO_8859_1);
    }

    public static void writeHttp(OutputStream out, String status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        out.write(("HTTP/1.1 " + status + "\r\nContent-Type: application/json\r\nContent-Length: " + bytes.length
                + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
        out.write(bytes);
        out.flush();
    }

    /** A valid proxied response of the proxychecker.co API. */
    public static final String JUDGE_RESPONSE =
            "{\"anonymity\":\"ELITE\",\"country\":\"Canada\",\"connected_ip\":\"1.1.1.1\"}";
}
