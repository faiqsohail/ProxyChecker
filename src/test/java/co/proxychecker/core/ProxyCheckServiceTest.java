package co.proxychecker.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import org.junit.jupiter.api.Test;

import co.proxychecker.components.net.ProxyFailure;

class ProxyCheckServiceTest {

    @Test
    void describesFailures() {
        assertEquals("timeout", ProxyCheckService.describe(new SocketTimeoutException("Read timed out")));
        assertEquals("connection refused", ProxyCheckService.describe(new ConnectException("Connection refused")));
        assertEquals("timeout", ProxyCheckService.describe(new ConnectException("Connection timed out")));
        assertEquals("unknown host", ProxyCheckService.describe(new UnknownHostException("x")));
        assertEquals("connection closed", ProxyCheckService.describe(new EOFException()));
        assertEquals("connection reset", ProxyCheckService.describe(new SocketException("Connection reset")));
        assertEquals("auth failed", ProxyCheckService.describe(new ProxyFailure(ProxyFailure.AUTH_FAILED, "x")));
        assertEquals("error: IOException", ProxyCheckService.describe(new IOException("x")));
    }
}
