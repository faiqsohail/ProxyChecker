package co.proxychecker.components;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

/**
 * Manages application constants and the user configuration file.
 * <p>
 * The configuration is loaded once and cached; {@link #saveConfig(UserSettings)} updates the cache, so
 * changes take effect immediately without re-reading the file from disk.
 */
public final class Settings {

    private static final Logger LOG = Logger.getLogger(Settings.class.getName());

    private static final String APPLICATION_VERSION = loadVersion();
    private static final String APPLICATION_NAME = "Proxy Checker";
    private static final String APPLICATION_URL = "https://proxychecker.co";
    private static final String APPLICATION_REPO = "https://github.com/faiqsohail/ProxyChecker/";
    private static final String CONFIG_FILE_NAME = "config.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static UserSettings config;

    /**
     * Reads the version injected by Maven into application.properties, so the version is defined only once.
     */
    private static String loadVersion() {
        try (InputStream in = Settings.class.getResourceAsStream("/co/proxychecker/application.properties")) {
            if (in != null) {
                Properties properties = new Properties();
                properties.load(in);
                String version = properties.getProperty("version");
                if (version != null && !version.isEmpty() && !version.startsWith("${")) {
                    return version;
                }
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Unable to read application version", e);
        }
        return "0.0";
    }

    private Settings() {
    }

    public static String getApplicationName() {
        return APPLICATION_NAME;
    }

    public static String getApplicationUrl() {
        return APPLICATION_URL;
    }

    public static String getApplicationVersion() {
        return APPLICATION_VERSION;
    }

    public static String getApplicationRepo() {
        return APPLICATION_REPO;
    }

    /**
     * Gets the user configuration, loading it from disk on first use. Never returns null: a missing,
     * unreadable or corrupt configuration file falls back to the defaults.
     */
    public static synchronized UserSettings getConfig() {
        if (config == null) {
            config = loadConfig(getConfigPath());
        }
        return config;
    }

    /**
     * Saves the given settings to disk and makes them the current configuration.
     *
     * @return whether the settings were written to disk successfully
     */
    public static synchronized boolean saveConfig(UserSettings userSettings) {
        config = userSettings;
        try {
            writeConfig(getConfigPath(), userSettings);
            return true;
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Unable to save configuration file", e);
            return false;
        }
    }

    static UserSettings loadConfig(Path path) {
        if (!Files.exists(path)) {
            UserSettings defaults = new UserSettings();
            try {
                writeConfig(path, defaults);
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Unable to write default configuration file", e);
            }
            return defaults;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            UserSettings settings = GSON.fromJson(reader, UserSettings.class);
            if (settings != null) {
                return settings.sanitize();
            }
        } catch (IOException | JsonParseException e) {
            // keep the broken file untouched so a hand edited configuration isn't silently lost
            LOG.log(Level.WARNING, "Unable to read configuration file " + path + ", using defaults", e);
        }
        return new UserSettings();
    }

    static void writeConfig(Path path, UserSettings settings) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        // write to a temporary file first so a crash mid-write cannot corrupt the existing configuration
        Path tmp = Files.createTempFile(parent, CONFIG_FILE_NAME, ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(settings, writer);
            }
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static Path getConfigPath() {
        return getSettingsFolder().toPath().resolve(CONFIG_FILE_NAME);
    }

    /**
     * Gets the settings folder in the user's home directory.
     */
    public static File getSettingsFolder() {
        String safeName = APPLICATION_NAME.replace(' ', '_');
        return new File(System.getProperty("user.home"), "." + safeName);
    }
}
