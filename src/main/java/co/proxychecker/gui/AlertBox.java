package co.proxychecker.gui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

import co.proxychecker.components.Settings;

/**
 * Shows simple alert dialogs.
 */
public final class AlertBox {

    private AlertBox() {
    }

    /**
     * Displays a non-blocking alert.
     *
     * @param alertType type of the alert
     * @param header    heading of the alert
     * @param content   content of the alert
     */
    public static void show(Alert.AlertType alertType, String header, String content) {
        Alert alert = new Alert(alertType, content, ButtonType.OK);
        Stage dialogStage = (Stage) alert.getDialogPane().getScene().getWindow();
        dialogStage.getIcons().add(Resources.icon());
        alert.setHeaderText(header);
        alert.setTitle(Settings.getApplicationName());
        alert.show();
    }
}
