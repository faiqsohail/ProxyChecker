package co.proxychecker.gui;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Opens modal application windows.
 */
public final class Window {

    private static final Logger LOG = Logger.getLogger(Window.class.getName());

    private Window() {
    }

    /**
     * Displays a modal window.
     *
     * @param title    title of the window
     * @param fxmlPath classpath location of the FXML view
     */
    public static void show(String title, String fxmlPath) {
        try {
            Parent root = new FXMLLoader(Resources.get(fxmlPath)).load();
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initStyle(StageStyle.DECORATED);
            stage.setTitle(title);
            stage.getIcons().add(Resources.icon());
            stage.setResizable(false);
            stage.setScene(new Scene(root));
            stage.show();
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.SEVERE, "Unable to open window " + fxmlPath, e);
            AlertBox.show(Alert.AlertType.ERROR, "Error", "Unable to open the " + title + " window.");
        }
    }
}
