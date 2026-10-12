package co.proxychecker.commands;

import co.proxychecker.components.RequestAPI;
import co.proxychecker.components.entities.Proxy;

/** Exposes package-private test hooks to tests in other packages. */
public final class ProxyCheckCommandAccess {

    private ProxyCheckCommandAccess() {
    }

    public static void testProxy(Proxy proxy, RequestAPI requestAPI) {
        ProxyCheckCommand.testProxy(proxy, requestAPI);
    }
}
