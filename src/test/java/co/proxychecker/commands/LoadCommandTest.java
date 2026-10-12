package co.proxychecker.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class LoadCommandTest {

    @Test
    void normalizesWhitespaceAndByteOrderMark() {
        assertEquals("1.2.3.4:80", LoadCommand.normalize("  1.2.3.4:80\t"));
        assertEquals("1.2.3.4:80", LoadCommand.normalize("\uFEFF1.2.3.4:80"));
        assertEquals("", LoadCommand.normalize("   "));
    }

    @Test
    void readsOnlyNewValidProxies() throws IOException {
        Set<String> known = new HashSet<>(Arrays.asList("1.1.1.1:80"));
        String file = "\uFEFF1.1.1.1:80\n2.2.2.2:8080\r\n garbage \n2.2.2.2:8080\n\n3.3.3.3:99999\n4.4.4.4:3128 \n";
        List<String> added = LoadCommand.readNewProxies(new BufferedReader(new StringReader(file)), known);
        assertEquals(Arrays.asList("2.2.2.2:8080", "4.4.4.4:3128"), added);
    }

    @Test
    void loadsLargeListsQuickly() throws IOException {
        // regression for #12: an ~11MB list used to freeze the application (O(n^2) de-duplication)
        StringBuilder file = new StringBuilder();
        for (int i = 0; i < 500_000; i++) {
            file.append(10 + (i >> 16)).append('.').append((i >> 8) & 255).append('.').append(i & 255)
                    .append(":").append(1 + i % 60000).append('\n');
        }
        long start = System.nanoTime();
        List<String> added = LoadCommand.readNewProxies(
                new BufferedReader(new StringReader(file.toString())), new HashSet<>());
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertEquals(500_000, added.size());
        assertTrue(millis < 10_000, "took " + millis + " ms");
    }
}
