package co.proxychecker.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import co.proxychecker.components.entities.Proxy;
import co.proxychecker.components.entities.ProxyAnonymity;
import co.proxychecker.components.entities.ProxyStatus;
import co.proxychecker.components.entities.ProxyType;

class ProgressReporterTest {

    @Test
    void describesWorkingProxies() {
        Proxy proxy = new Proxy("1.0.133.89:4153", ProxyType.SOCKS4)
                .setProxyStatus(ProxyStatus.ALIVE).setProxyAnonymity(ProxyAnonymity.ANONYMOUS)
                .setCountry("US").setResponseTimeMillis(812);
        assertEquals("[WORKING] 1.0.133.89:4153 socks4 anonymous US 812ms", ProgressReporter.describe(proxy));
    }

    @Test
    void describesDeadProxiesWithoutPasswords() {
        Proxy proxy = new Proxy("alice:secret@10.0.0.1:1080", ProxyType.SOCKS5)
                .setProxyStatus(ProxyStatus.DEAD).setFailureReason("auth failed");
        assertEquals("[DEAD] alice@10.0.0.1:1080 socks5 auth failed", ProgressReporter.describe(proxy));
    }
}
