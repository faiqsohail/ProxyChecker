package co.proxychecker.core;

import java.io.IOException;
import java.util.List;

import co.proxychecker.components.entities.Proxy;
import co.proxychecker.components.entities.ProxyAnonymity;
import co.proxychecker.components.entities.ProxyStatus;

/**
 * Writes checked proxies as plain text or CSV.
 */
public final class ProxyExporter {

    public static final String CSV_HEADER =
            "IP,Port,Username,Password,Type,Status,Anonymity,Country,Response Time";

    private ProxyExporter() {
    }

    /**
     * Writes one proxy per line, exactly as it was loaded (credentials included).
     */
    public static void writeText(List<Proxy> proxies, Appendable out) throws IOException {
        for (Proxy proxy : proxies) {
            out.append(proxy.getSpec()).append('\n');
        }
    }

    /**
     * Writes the proxies as CSV (RFC 4180) with a header row.
     */
    public static void writeCsv(List<Proxy> proxies, Appendable out) throws IOException {
        out.append(CSV_HEADER).append('\n');
        for (Proxy proxy : proxies) {
            out.append(csvField(proxy.getIp())).append(',')
                    .append(String.valueOf(proxy.getPort())).append(',')
                    .append(csvField(proxy.getUsername())).append(',')
                    .append(csvField(proxy.getPassword())).append(',')
                    .append(csvField(proxy.getProxyType())).append(',')
                    .append(csvField(proxy.getProxyStatus())).append(',')
                    .append(csvField(proxy.getProxyAnonymity())).append(',')
                    .append(csvField(proxy.getCountry())).append(',')
                    .append(csvField(proxy.getResponseTime())).append('\n');
        }
    }

    /**
     * Quotes a CSV field when needed, e.g. country names such as "Korea, Republic of". Null becomes empty.
     */
    public static String csvField(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }

    /**
     * @param proxyAnonymity required anonymity, or null for any
     */
    public static boolean matches(Proxy proxy, ProxyStatus proxyStatus, ProxyAnonymity proxyAnonymity) {
        return proxy.getProxyStatus() == proxyStatus
                && (proxyAnonymity == null || proxyAnonymity == proxy.getProxyAnonymity());
    }
}
