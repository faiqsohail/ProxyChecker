package co.proxychecker.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import co.proxychecker.components.entities.ProxyAnonymity;

class SettingsTest {

    @TempDir
    Path dir;

    @Test
    void missingConfigCreatesDefaults() {
        Path config = dir.resolve("config.json");
        UserSettings settings = Settings.loadConfig(config);
        assertEquals(UserSettings.DEFAULT_THREADS, settings.getThreads());
        assertTrue(Files.exists(config));
    }

    @Test
    void corruptConfigFallsBackToDefaults() throws IOException {
        Path config = dir.resolve("config.json");
        Files.write(config, "{ not json".getBytes(StandardCharsets.UTF_8));
        UserSettings settings = Settings.loadConfig(config);
        assertEquals(UserSettings.DEFAULT_TIMEOUT, settings.getTimeout());
        assertNotNull(settings.getColorScheme());
        assertEquals("{ not json", new String(Files.readAllBytes(config), StandardCharsets.UTF_8));
    }

    @Test
    void readsConfigWrittenByPreviousVersions() throws IOException {
        // format written by v1.2 (javafx.util.Pair serialized by Gson)
        String json = "{\"timeout\":3000,\"threads\":50,\"ip\":\"1.2.3.4\",\"type\":\"SOCKS\","
                + "\"colorScheme\":[{\"key\":\"ELITE\",\"value\":\"#010203\"},"
                + "{\"key\":\"ANONYMOUS\",\"value\":\"#e6e6b3\"},{\"key\":\"TRANSPARENT\",\"value\":\"#ffb3b3\"}]}";
        Path config = dir.resolve("config.json");
        Files.write(config, json.getBytes(StandardCharsets.UTF_8));

        UserSettings settings = Settings.loadConfig(config);
        assertEquals(3000, settings.getTimeout());
        assertEquals(50, settings.getThreads());
        assertEquals("1.2.3.4", settings.getIp());
        assertEquals(Proxy.Type.SOCKS, settings.getProxyType());
        assertEquals("#010203", settings.getColor(ProxyAnonymity.ELITE));
    }

    @Test
    void sanitizesOutOfRangeValues() throws IOException {
        Path config = dir.resolve("config.json");
        Files.write(config, "{\"timeout\":0,\"threads\":999999,\"type\":\"BOGUS\",\"colorScheme\":null}"
                .getBytes(StandardCharsets.UTF_8));
        UserSettings settings = Settings.loadConfig(config);
        assertEquals(UserSettings.DEFAULT_TIMEOUT, settings.getTimeout());
        assertEquals(UserSettings.MAX_THREADS, settings.getThreads());
        assertEquals(Proxy.Type.HTTP, settings.getProxyType());
        assertEquals("#b3e6b3", settings.getColor(ProxyAnonymity.ELITE));
    }

    @Test
    void roundTripsSettings() throws IOException {
        Path config = dir.resolve("nested").resolve("config.json");
        Settings.writeConfig(config, new UserSettings().setThreads(7).setProxyType(Proxy.Type.SOCKS));
        UserSettings settings = Settings.loadConfig(config);
        assertEquals(7, settings.getThreads());
        assertEquals(Proxy.Type.SOCKS, settings.getProxyType());
    }
}
