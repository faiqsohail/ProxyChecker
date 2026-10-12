package co.proxychecker.gui;

import java.net.URL;

import javafx.scene.image.Image;

/**
 * Locations of bundled GUI resources.
 */
public final class Resources {

    public static final String MAIN_VIEW = "/co/proxychecker/gui/ProxyChecker.fxml";
    public static final String SETTINGS_VIEW = "/co/proxychecker/gui/Settings.fxml";
    public static final String ABOUT_VIEW = "/co/proxychecker/gui/About.fxml";
    private static final String ICON = "/co/proxychecker/assets/icon.png";

    private static Image icon;

    private Resources() {
    }

    public static URL get(String path) {
        URL url = Resources.class.getResource(path);
        if (url == null) {
            throw new IllegalStateException("Missing bundled resource: " + path);
        }
        return url;
    }

    /**
     * @return the application icon, loaded once and shared by all windows
     */
    public static synchronized Image icon() {
        if (icon == null) {
            icon = new Image(get(ICON).toExternalForm());
        }
        return icon;
    }
}
