package co.proxychecker.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import co.proxychecker.components.entities.Proxy;

/**
 * Reads proxy lists (one proxy per line).
 */
public final class ProxyListReader {

    private ProxyListReader() {
    }

    /**
     * Reads valid proxies that aren't in {@code known} yet, adding them to {@code known}. Uses a set,
     * so de-duplication is O(1) per line even for very large lists.
     */
    public static List<String> readNewProxies(BufferedReader reader, Set<String> known) throws IOException {
        List<String> added = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            String value = normalize(line);
            if (Proxy.isValidFormat(value) && known.add(value)) {
                added.add(value);
            }
        }
        return added;
    }

    /**
     * Trims whitespace and a UTF-8 byte order mark, so "1.2.3.4:80 " and "1.2.3.4:80" are the same proxy.
     */
    public static String normalize(String line) {
        String value = line.trim();
        if (!value.isEmpty() && value.charAt(0) == '\uFEFF') {
            value = value.substring(1).trim();
        }
        return value;
    }
}
