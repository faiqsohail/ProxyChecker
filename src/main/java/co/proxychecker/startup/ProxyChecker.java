package co.proxychecker.startup;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

import co.proxychecker.components.RequestAPI;
import co.proxychecker.components.Settings;
import co.proxychecker.components.UpdateChecker;
import co.proxychecker.components.UserSettings;
import co.proxychecker.gui.AlertBox;
import co.proxychecker.gui.Browser;
import co.proxychecker.gui.Resources;

/**
 * JavaFX application: checks for updates, determines the public IP address and opens the main window.
 */
public class ProxyChecker extends Application {

    private static final Logger LOG = Logger.getLogger(ProxyChecker.class.getName());

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        Browser.init(getHostServices());

        if (promptForUpdate()) {
            return;
        }

        UserSettings settings = Settings.getConfig();
        String ip = fetchPublicIp(settings);
        if (ip == null) {
            AlertBox.show(
                    Alert.AlertType.ERROR,
                    "Service Down",
                    "Unable to get current public IP address, "
                            + "will not be able to determine proxy status and anonymity!\n\n"
                            + "Check your internet connection and try again later, you may also visit "
                            + Settings.getApplicationUrl() + " for support."
            );
            return;
        }
        if (!ip.equals(settings.getIp())) {
            Settings.saveConfig(settings.setIp(ip));
        }

        Parent root = new FXMLLoader(Resources.get(Resources.MAIN_VIEW)).load();
        primaryStage.setScene(new Scene(root));
        primaryStage.getIcons().add(Resources.icon());
        primaryStage.setTitle(Settings.getApplicationName());
        primaryStage.setOnCloseRequest(event -> {
            Platform.exit();
            System.exit(0);
        });
        primaryStage.show();
    }

    /**
     * Notifies the user if a newer version is available.
     *
     * @return true if the user chose to download the update and the application should quit
     */
    private boolean promptForUpdate() {
        UpdateChecker checker = new UpdateChecker();
        if (!checker.isUpdateAvailable()) {
            return false;
        }
        String repoVersion;
        try {
            repoVersion = checker.getRepoVersion();
        } catch (IOException e) {
            return false;
        }
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setHeaderText("Update");
        alert.setTitle("A new update is available!");
        alert.setContentText("A new update for " + Settings.getApplicationName()
                + " is available would you like to download it now? \n\n"
                + "Current Version: " + Settings.getApplicationVersion() + "\n"
                + "New Version: " + repoVersion);
        alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
        boolean download = alert.showAndWait().filter(type -> type == ButtonType.YES).isPresent();
        if (download) {
            Browser.open(Settings.getApplicationRepo() + "releases/latest");
            Platform.exit();
            return true;
        }
        Alert notice = new Alert(Alert.AlertType.WARNING);
        notice.setHeaderText("Notice");
        notice.setContentText("Support is not provided for outdated versions of the application.\n\n"
                + "Outdated version(s) of the application may cease to work at anytime. ");
        notice.showAndWait();
        return false;
    }

    /**
     * @return the user's public IP address as seen by the API server, or null if it can't be determined
     */
    private static String fetchPublicIp(UserSettings settings) {
        try {
            String ip = new RequestAPI(settings).request(null).getResponse().ip;
            return (ip == null || ip.trim().isEmpty()) ? null : ip.trim();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Unable to determine public IP address", e);
            return null;
        }
    }
}
