package co.proxychecker.components.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.proxychecker.components.entities.ProxyType;

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
        Proxy proxy = new Proxy(" 10.0.0.1:3128 ", ProxyType.SOCKS5);
        assertEquals("10.0.0.1", proxy.getIp());
        assertEquals(3128, proxy.getPort());
        assertEquals(ProxyType.SOCKS5, proxy.getProxyType());
        assertEquals(ProxyStatus.DEAD, proxy.getProxyStatus());
    }

    @Test
    void parsesCredentialsInBothFormats() {
        Proxy a = new Proxy("alice:s3cr3t@10.0.0.1:1080", ProxyType.SOCKS5);
        assertEquals("10.0.0.1", a.getIp());
        assertEquals(1080, a.getPort());
        assertEquals("alice", a.getUsername());
        assertEquals("s3cr3t", a.getPassword());
        assertTrue(a.hasCredentials());
        assertEquals("alice:s3cr3t@10.0.0.1:1080", a.getSpec());

        Proxy b = new Proxy("proxy.example.com:3128:bob:pa:ss", ProxyType.HTTP);
        assertEquals("proxy.example.com", b.getIp());
        assertEquals(3128, b.getPort());
        assertEquals("bob", b.getUsername());
        assertEquals("pa:ss", b.getPassword());

        Proxy c = new Proxy("u:p@ss:w0rd@1.2.3.4:80", ProxyType.HTTP); // '@' allowed in the password
        assertEquals("u", c.getUsername());
        assertEquals("p@ss:w0rd", c.getPassword());
        assertEquals("1.2.3.4", c.getIp());

        Proxy d = new Proxy("u:@1.2.3.4:80", ProxyType.HTTP); // empty password
        assertEquals("", d.getPassword());

        Proxy plain = new Proxy("1.2.3.4:80", ProxyType.HTTP);
        assertFalse(plain.hasCredentials());
        assertNull(plain.getUsername());
    }

    @Test
    void rejectsInvalidCredentialFormats() {
        assertFalse(Proxy.isValidFormat("@1.2.3.4:80"));
        assertFalse(Proxy.isValidFormat("user@1.2.3.4:80"));
        assertFalse(Proxy.isValidFormat(":pass@1.2.3.4:80"));
        assertFalse(Proxy.isValidFormat("u:p@1.2.3.4"));
        assertFalse(Proxy.isValidFormat("u:p@1.2.3.4:99999"));
        assertFalse(Proxy.isValidFormat("1.2.3.4:80:user"));
        assertFalse(Proxy.isValidFormat("1.2.3.4:80::pass"));
    }

    @Test
    void toStringNeverContainsCredentials() {
        assertEquals("1.2.3.4:80", new Proxy("user:secret@1.2.3.4:80", ProxyType.HTTP).toString());
    }

    @Test
    void rejectsInvalidConstructorInput() {
        assertThrows(IllegalArgumentException.class, () -> new Proxy("nope", ProxyType.HTTP));
    }
}
