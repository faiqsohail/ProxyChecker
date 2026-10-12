package co.proxychecker.components.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.google.gson.Gson;

class ProxyTypeTest {

    @Test
    void parsesNamesCaseInsensitively() {
        assertEquals(ProxyType.HTTP, ProxyType.fromString("http"));
        assertEquals(ProxyType.SOCKS4, ProxyType.fromString("socks4"));
        assertEquals(ProxyType.SOCKS4, ProxyType.fromString("SOCKS4a"));
        assertEquals(ProxyType.SOCKS5, ProxyType.fromString(" Socks5 "));
        assertEquals(ProxyType.SOCKS5, ProxyType.fromString("socks"));
        assertThrows(IllegalArgumentException.class, () -> ProxyType.fromString("ftp"));
    }

    @Test
    void readsLegacySocksValueAsSocks5() {
        Gson gson = new Gson();
        assertEquals(ProxyType.SOCKS5, gson.fromJson("\"SOCKS\"", ProxyType.class));
        assertEquals(ProxyType.SOCKS5, gson.fromJson("\"SOCKS5\"", ProxyType.class));
        assertEquals(ProxyType.SOCKS4, gson.fromJson("\"SOCKS4\"", ProxyType.class));
        assertEquals("\"SOCKS5\"", gson.toJson(ProxyType.SOCKS5));
    }
}
