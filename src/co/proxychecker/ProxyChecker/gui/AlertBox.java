package co.proxychecker.ProxyChecker.gui;

import javafx.stage.Stage;
import javafx.scene.image.Image;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import co.proxychecker.ProxyChecker.components.Settings;

/**
 * Creates an AlertBox
 */
public class AlertBox {

    /**
     * Displays an AlertBox
     * @param alertType - javafx.scene.control.Alert.AlertType
     * @param header - Heading of the Alert
     * @param content - Content of the Alert
     */
    public static void show(Alert.AlertType alertType, String header, String content) {
        Alert alert = new Alert(alertType, content, ButtonType.OK);
        Stage dialogStage = (Stage) alert.getDialogPane().getScene().getWindow();
        dialogStage.getIcons().add(
                new Image(AlertBox.class.getResourceAsStream("/co/proxychecker/ProxyChecker/assets/icon.png"))
        );
        alert.setHeaderText(header);
        alert.setTitle(Settings.getApplicationName());
        alert.show();
    }
}
