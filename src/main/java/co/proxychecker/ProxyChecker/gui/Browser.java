package co.proxychecker.ProxyChecker.gui;

import java.util.logging.Logger;

import javafx.application.HostServices;

/**
 * Opens web pages in the user's default browser using JavaFX {@link HostServices}.
 * (AWT's {@code Desktop.browse} is unsupported on some Linux desktops and can deadlock when
 * called from the JavaFX application thread on macOS.)
 */
public final class Browser {

    private static final Logger LOG = Logger.getLogger(Browser.class.getName());
    private static volatile HostServices hostServices;

    private Browser() {
    }

    public static void init(HostServices services) {
        hostServices = services;
    }

    public static void open(String url) {
        HostServices services = hostServices;
        if (services == null) {
            LOG.warning("Unable to open " + url + ": host services not initialised");
            return;
        }
        services.showDocument(url);
    }
}
