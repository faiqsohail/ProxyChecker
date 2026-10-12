package co.proxychecker.components.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.Proxy.Type;

import org.junit.jupiter.api.Test;

class ProxyTest {

    @Test
    void acceptsValidProxies() {
        assertTrue(Proxy.isValidFormat("1.2.3.4:8080"));
        assertTrue(Proxy.isValidFormat("proxy.example.com:1"));
        assertTrue(Proxy.isValidFormat("1.2.3.4:65535"));
        assertTrue(Proxy.isValidFormat("  1.2.3.4:80  "));
    }

    @Test
    void rejectsInvalidProxies() {
        assertFalse(Proxy.isValidFormat(null));
        assertFalse(Proxy.isValidFormat(""));
        assertFalse(Proxy.isValidFormat("1.2.3.4"));
        assertFalse(Proxy.isValidFormat(":8080"));
        assertFalse(Proxy.isValidFormat("1.2.3.4:"));
        assertFalse(Proxy.isValidFormat("1.2.3.4:abc"));
        assertFalse(Proxy.isValidFormat("1.2.3.4:0"));
        assertFalse(Proxy.isValidFormat("1.2.3.4:65536"));
        assertFalse(Proxy.isValidFormat("1.2.3.4:-1"));
        assertFalse(Proxy.isValidFormat("1.2.3.4:80:90"));
    }

    @Test
    void parsesHostAndPort() {
        Proxy proxy = new Proxy(" 10.0.0.1:3128 ", Type.SOCKS);
        assertEquals("10.0.0.1", proxy.getIp());
        assertEquals(3128, proxy.getPort());
        assertEquals(Type.SOCKS, proxy.getProxyType());
        assertEquals(ProxyStatus.DEAD, proxy.getProxyStatus());
    }

    @Test
    void rejectsInvalidConstructorInput() {
        assertThrows(IllegalArgumentException.class, () -> new Proxy("nope", Type.HTTP));
    }
}
