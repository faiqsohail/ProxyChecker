package co.proxychecker.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

class UpdateCheckerTest {

    @Test
    void comparesVersionsNumerically() {
        assertTrue(UpdateChecker.compareVersions("1.3", "1.2") > 0);
        assertTrue(UpdateChecker.compareVersions("1.2", "1.3") < 0);
        assertTrue(UpdateChecker.compareVersions("1.10", "1.9") > 0);
        assertTrue(UpdateChecker.compareVersions("2.0", "1.10") > 0);
        assertEquals(0, UpdateChecker.compareVersions("1.2", "1.2.0"));
        assertEquals(0, UpdateChecker.compareVersions("v1.2", "1.2"));
        assertTrue(UpdateChecker.compareVersions("1.2.1", "1.2") > 0);
    }

    @Test
    void rejectsNonNumericVersions() {
        assertThrows(IllegalArgumentException.class, () -> UpdateChecker.compareVersions("latest", "1.2"));
    }

    @Test
    void extractsTagFromRedirect() throws IOException {
        assertEquals("1.2", UpdateChecker.extractTag(
                "https://github.com/faiqsohail/ProxyChecker/releases/tag/1.2"));
        assertThrows(IOException.class, () -> UpdateChecker.extractTag(null));
        assertThrows(IOException.class, () -> UpdateChecker.extractTag(
                "https://github.com/faiqsohail/ProxyChecker/releases"));
    }
}
