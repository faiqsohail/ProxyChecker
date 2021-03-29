package co.proxychecker.ProxyChecker.gui;

import javafx.stage.Stage;
import javafx.scene.image.Image;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import co.proxychecker.ProxyChecker.components.Settings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Objects;

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
        try {
            dialogStage.getIcons().add(
    //                new Image(Objects.requireNonNull(AlertBox.class.getResourceAsStream("/co/proxychecker/ProxyChecker/assets/icon.png")))
                    new Image( new FileInputStream( new File("C:\\Users\\User\\IdeaProjects\\ProxyChecker_gui\\src\\main\\java\\co\\proxychecker\\ProxyChecker\\assets\\icon.png").getAbsolutePath()))
            );
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        alert.setHeaderText(header);
        alert.setTitle(Settings.getApplicationName());
        alert.show();
    }
}
