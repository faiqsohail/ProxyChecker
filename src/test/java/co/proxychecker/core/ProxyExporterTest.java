package co.proxychecker.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import co.proxychecker.components.entities.ProxyType;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import co.proxychecker.components.entities.Proxy;
import co.proxychecker.components.entities.ProxyAnonymity;
import co.proxychecker.components.entities.ProxyStatus;

class ProxyExporterTest {

    @Test
    void escapesCsvFields() {
        assertEquals("", ProxyExporter.csvField(null));
        assertEquals("plain", ProxyExporter.csvField("plain"));
        assertEquals("\"Korea, Republic of\"", ProxyExporter.csvField("Korea, Republic of"));
        assertEquals("\"say \"\"hi\"\"\"", ProxyExporter.csvField("say \"hi\""));
    }

    @Test
    void writesCsvWithHeader() throws IOException {
        Proxy alive = new Proxy("1.2.3.4:80", ProxyType.HTTP);
        alive.setProxyStatus(ProxyStatus.ALIVE).setProxyAnonymity(ProxyAnonymity.ELITE)
                .setCountry("Korea, Republic of").setResponseTime("12 (ms)");
        Proxy dead = new Proxy("bob:p,w@5.6.7.8:8080", ProxyType.SOCKS5);
        dead.setProxyAnonymity(null);

        StringBuilder out = new StringBuilder();
        ProxyExporter.writeCsv(Arrays.asList(alive, dead), out);

        assertEquals(ProxyExporter.CSV_HEADER + "\n"
                + "1.2.3.4,80,,,HTTP,ALIVE,ELITE,\"Korea, Republic of\",12 (ms)\n"
                + "5.6.7.8,8080,bob,\"p,w\",SOCKS5,DEAD,,,\n", out.toString());
    }

    @Test
    void filtersByStatusAndAnonymity() {
        Proxy proxy = new Proxy("1.2.3.4:80", ProxyType.HTTP);
        proxy.setProxyStatus(ProxyStatus.ALIVE).setProxyAnonymity(ProxyAnonymity.ANONYMOUS);
        assertTrue(ProxyExporter.matches(proxy, ProxyStatus.ALIVE, null));
        assertTrue(ProxyExporter.matches(proxy, ProxyStatus.ALIVE, ProxyAnonymity.ANONYMOUS));
        assertFalse(ProxyExporter.matches(proxy, ProxyStatus.ALIVE, ProxyAnonymity.ELITE));
        assertFalse(ProxyExporter.matches(proxy, ProxyStatus.DEAD, null));
    }
}
