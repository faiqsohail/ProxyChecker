package co.proxychecker.ProxyChecker.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.Proxy.Type;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import co.proxychecker.ProxyChecker.components.entities.Proxy;
import co.proxychecker.ProxyChecker.components.entities.ProxyAnonymity;
import co.proxychecker.ProxyChecker.components.entities.ProxyStatus;

class ExportCommandTest {

    @Test
    void escapesCsvFields() {
        assertEquals("", ExportCommand.csvField(null));
        assertEquals("plain", ExportCommand.csvField("plain"));
        assertEquals("\"Korea, Republic of\"", ExportCommand.csvField("Korea, Republic of"));
        assertEquals("\"say \"\"hi\"\"\"", ExportCommand.csvField("say \"hi\""));
    }

    @Test
    void writesCsvWithHeader() throws IOException {
        Proxy alive = new Proxy("1.2.3.4:80", Type.HTTP);
        alive.setProxyStatus(ProxyStatus.ALIVE).setProxyAnonymity(ProxyAnonymity.ELITE)
                .setCountry("Korea, Republic of").setResponseTime("12 (ms)");
        Proxy dead = new Proxy("5.6.7.8:8080", Type.HTTP);
        dead.setProxyAnonymity(null);

        StringBuilder out = new StringBuilder();
        ExportCommand.writeCsv(Arrays.asList(alive, dead), out);

        assertEquals(ExportCommand.CSV_HEADER + "\n"
                + "1.2.3.4,80,ALIVE,ELITE,\"Korea, Republic of\",12 (ms)\n"
                + "5.6.7.8,8080,DEAD,,,\n", out.toString());
    }

    @Test
    void filtersByStatusAndAnonymity() {
        Proxy proxy = new Proxy("1.2.3.4:80", Type.HTTP);
        proxy.setProxyStatus(ProxyStatus.ALIVE).setProxyAnonymity(ProxyAnonymity.ANONYMOUS);
        assertTrue(ExportCommand.matches(proxy, ProxyStatus.ALIVE, null));
        assertTrue(ExportCommand.matches(proxy, ProxyStatus.ALIVE, ProxyAnonymity.ANONYMOUS));
        assertFalse(ExportCommand.matches(proxy, ProxyStatus.ALIVE, ProxyAnonymity.ELITE));
        assertFalse(ExportCommand.matches(proxy, ProxyStatus.DEAD, null));
    }
}
