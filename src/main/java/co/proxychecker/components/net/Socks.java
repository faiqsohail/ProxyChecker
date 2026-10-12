package co.proxychecker.components.net;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Client side SOCKS4/4a (http://www.openssh.com/txt/socks4.protocol) and SOCKS5 (RFC 1928, RFC 1929)
 * handshakes. Implemented by hand because the JDK has no SOCKS4 client and so every connection carries
 * its own settings.
 */
final class Socks {

    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    private static final int SOCKS4_VERSION = 0x04;
    private static final int SOCKS4_CONNECT = 0x01;
    private static final int SOCKS4_GRANTED = 0x5A;

    private static final int SOCKS5_VERSION = 0x05;
    private static final int SOCKS5_NO_AUTH = 0x00;
    private static final int SOCKS5_USER_PASS = 0x02;
    private static final int SOCKS5_NO_ACCEPTABLE = 0xFF;
    private static final int SOCKS5_CONNECT = 0x01;
    private static final int SOCKS5_ATYP_IPV4 = 0x01;
    private static final int SOCKS5_ATYP_DOMAIN = 0x03;
    private static final int SOCKS5_ATYP_IPV6 = 0x04;

    private Socks() {
    }

    /**
     * Performs a SOCKS4 CONNECT. Uses the target's IPv4 address when it can be resolved locally
     * (plain SOCKS4, supported by every server) and SOCKS4a remote resolution otherwise.
     *
     */
    static void connect4(InputStream in, OutputStream out, String host, int port) throws IOException {
        connect4(in, out, host, port, Socks::resolveIPv4);
    }

    /** Resolves a host name to an IPv4 address, or null if it can't be resolved. */
    @FunctionalInterface
    interface Resolver {
        byte[] resolve(String host);
    }

    static void connect4(InputStream in, OutputStream out, String host, int port, Resolver resolver)
            throws IOException {
        byte[] address = resolver.resolve(host);
        boolean socks4a = address == null;
        if (socks4a) {
            address = new byte[] {0, 0, 0, 1}; // SOCKS4a marker: 0.0.0.x with x != 0
        }
        out.write(SOCKS4_VERSION);
        out.write(SOCKS4_CONNECT);
        writePort(out, port);
        out.write(address);
        out.write(0); // empty user id
        if (socks4a) {
            out.write(host.getBytes(StandardCharsets.US_ASCII));
            out.write(0);
        }
        out.flush();

        byte[] reply = readFully(in, 8);
        if (reply[0] != 0x00 && reply[0] != SOCKS4_VERSION) {
            throw new IOException("Invalid SOCKS4 reply");
        }
        int status = reply[1] & 0xFF;
        if (status != SOCKS4_GRANTED) {
            throw new IOException("SOCKS4 request rejected (code 0x" + Integer.toHexString(status) + ")");
        }
    }

    /**
     * Performs a SOCKS5 CONNECT using the domain name of the target (remote DNS resolution).
     */
    static void connect5(InputStream in, OutputStream out, String host, int port) throws IOException {
        out.write(new byte[] {SOCKS5_VERSION, 1, SOCKS5_NO_AUTH});
        out.flush();

        byte[] choice = readFully(in, 2);
        if (choice[0] != SOCKS5_VERSION) {
            throw new IOException("Invalid SOCKS5 reply");
        }
        int method = choice[1] & 0xFF;
        if (method == SOCKS5_USER_PASS) {
            throw new IOException("SOCKS5 proxy requires authentication");
        } else if (method == SOCKS5_NO_ACCEPTABLE) {
            throw new IOException("SOCKS5 proxy accepted none of the offered authentication methods");
        } else if (method != SOCKS5_NO_AUTH) {
            throw new IOException("Unsupported SOCKS5 authentication method " + method);
        }

        out.write(SOCKS5_VERSION);
        out.write(SOCKS5_CONNECT);
        out.write(0x00);
        if (IPV4.matcher(host).matches()) {
            out.write(SOCKS5_ATYP_IPV4);
            out.write(InetAddress.getByName(host).getAddress());
        } else {
            byte[] name = host.getBytes(StandardCharsets.US_ASCII);
            if (name.length > 255) {
                throw new IOException("Host name too long for SOCKS5");
            }
            out.write(SOCKS5_ATYP_DOMAIN);
            out.write(name.length);
            out.write(name);
        }
        writePort(out, port);
        out.flush();

        byte[] reply = readFully(in, 4);
        if (reply[0] != SOCKS5_VERSION) {
            throw new IOException("Invalid SOCKS5 reply");
        }
        if (reply[1] != 0x00) {
            throw new IOException("SOCKS5 connect failed (code " + (reply[1] & 0xFF) + ")");
        }
        int addressLength;
        switch (reply[3] & 0xFF) {
            case SOCKS5_ATYP_IPV4:
                addressLength = 4;
                break;
            case SOCKS5_ATYP_IPV6:
                addressLength = 16;
                break;
            case SOCKS5_ATYP_DOMAIN:
                addressLength = readFully(in, 1)[0] & 0xFF;
                break;
            default:
                throw new IOException("Invalid SOCKS5 address type");
        }
        readFully(in, addressLength + 2); // bound address and port, unused
    }

    /**
     * @return the IPv4 address of the host, or null if it can't be resolved to one locally
     */
    private static byte[] resolveIPv4(String host) {
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address instanceof Inet4Address) {
                    return address.getAddress();
                }
            }
        } catch (UnknownHostException e) {
            // let the proxy resolve it (SOCKS4a)
        }
        return null;
    }

    private static void writePort(OutputStream out, int port) throws IOException {
        out.write((port >> 8) & 0xFF);
        out.write(port & 0xFF);
    }

    static byte[] readFully(InputStream in, int length) throws IOException {
        byte[] buffer = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = in.read(buffer, offset, length - offset);
            if (read < 0) {
                throw new EOFException("Connection closed by proxy");
            }
            offset += read;
        }
        return buffer;
    }
}
