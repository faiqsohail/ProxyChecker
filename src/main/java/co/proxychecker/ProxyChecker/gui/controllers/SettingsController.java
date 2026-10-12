package co.proxychecker.ProxyChecker.gui.controllers;

import java.net.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;

import co.proxychecker.ProxyChecker.components.Colors;
import co.proxychecker.ProxyChecker.components.Settings;
import co.proxychecker.ProxyChecker.components.UserSettings;
import co.proxychecker.ProxyChecker.components.entities.AnonymityColor;
import co.proxychecker.ProxyChecker.components.entities.ProxyAnonymity;
import co.proxychecker.ProxyChecker.gui.AlertBox;

/**
 * Controller for Settings.fxml
 */
public class SettingsController implements Initializable {

    @FXML
    private Button button_save;

    @FXML
    private TextField field_threads;

    @FXML
    private TextField field_timeout;

    @FXML
    private ComboBox<String> combo_type;

    @FXML
    private ColorPicker color_elite;

    @FXML
    private ColorPicker color_anonymous;

    @FXML
    private ColorPicker color_transparent;

    private final UserSettings settings = Settings.getConfig();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        combo_type.getItems().addAll(Proxy.Type.HTTP.name(), Proxy.Type.SOCKS.name());

        // show current settings
        combo_type.getSelectionModel().select(settings.getProxyType().name());
        field_timeout.setText(String.valueOf(settings.getTimeout()));
        field_threads.setText(String.valueOf(settings.getThreads()));
        initColor(color_elite, ProxyAnonymity.ELITE);
        initColor(color_anonymous, ProxyAnonymity.ANONYMOUS);
        initColor(color_transparent, ProxyAnonymity.TRANSPARENT);

        combo_type.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> updateSaveButton());
        field_timeout.textProperty().addListener((observable, oldValue, newValue) ->
                integerTextField(field_timeout, oldValue, newValue));
        field_threads.textProperty().addListener((observable, oldValue, newValue) ->
                integerTextField(field_threads, oldValue, newValue));

        button_save.setDisable(true); // nothing to save yet
    }

    private void initColor(ColorPicker picker, ProxyAnonymity anonymity) {
        String color = settings.getColor(anonymity);
        if (color != null) {
            try {
                picker.setValue(Color.web(color));
            } catch (IllegalArgumentException e) {
                // invalid color in the config file, keep the picker default
            }
        }
    }

    /**
     * Saves the proxy settings
     */
    @FXML
    private void button_save_action() {
        Integer threads = parse(field_threads.getText());
        Integer timeout = parse(field_timeout.getText());

        if (timeout == null || timeout < UserSettings.MIN_TIMEOUT) {
            // a timeout that is too low results in no response from the API server at all
            AlertBox.show(Alert.AlertType.ERROR, "Timeout Too Low",
                    "The minimum accepted timeout value for requests is " + UserSettings.MIN_TIMEOUT
                            + " milliseconds.");
        } else if (threads == null || threads < UserSettings.MIN_THREADS || threads > UserSettings.MAX_THREADS) {
            AlertBox.show(Alert.AlertType.ERROR, "Invalid Thread Count",
                    "The number of threads must be between " + UserSettings.MIN_THREADS + " and "
                            + UserSettings.MAX_THREADS + ".");
        } else {
            Proxy.Type type = Proxy.Type.valueOf(combo_type.getSelectionModel().getSelectedItem());
            settings.setThreads(threads).setTimeout(timeout).setProxyType(type);
            if (Settings.saveConfig(settings)) {
                AlertBox.show(Alert.AlertType.INFORMATION, "Changes Saved",
                        "The changes made have been saved to disk!\n\n"
                                + "They will be used for the next proxy check.");
                button_save.setDisable(true);
            } else {
                AlertBox.show(Alert.AlertType.ERROR, "Save Failed",
                        "Unable to save the configuration file onto disk!");
            }
        }
    }

    /**
     * Saves the color preferences
     */
    @FXML
    private void button_save_color_action() {
        List<AnonymityColor> newScheme = new ArrayList<>();
        newScheme.add(new AnonymityColor(ProxyAnonymity.ELITE, toHex(color_elite.getValue())));
        newScheme.add(new AnonymityColor(ProxyAnonymity.ANONYMOUS, toHex(color_anonymous.getValue())));
        newScheme.add(new AnonymityColor(ProxyAnonymity.TRANSPARENT, toHex(color_transparent.getValue())));

        settings.setColorScheme(newScheme);
        if (Settings.saveConfig(settings)) {
            AlertBox.show(Alert.AlertType.INFORMATION, "Changes Saved",
                    "The changes made have been saved to disk!");
        } else {
            AlertBox.show(Alert.AlertType.ERROR, "Save Failed",
                    "Unable to save the configuration file onto disk!");
        }
    }

    private static String toHex(Color color) {
        return Colors.toHex(color.getRed(), color.getGreen(), color.getBlue());
    }

    private static Integer parse(String text) {
        try {
            return Integer.valueOf(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Determines whether any proxy setting value has been changed
     */
    private boolean settingsChanged() {
        return !settings.getProxyType().name().equals(combo_type.getSelectionModel().getSelectedItem())
                || !String.valueOf(settings.getTimeout()).equals(field_timeout.getText())
                || !String.valueOf(settings.getThreads()).equals(field_threads.getText());
    }

    private void updateSaveButton() {
        button_save.setDisable(!settingsChanged());
    }

    /**
     * Makes sure the user is only able to enter a positive number in the text field
     */
    private void integerTextField(TextField field, String oldValue, String newValue) {
        if (!newValue.isEmpty()) {
            Integer value = parse(newValue);
            if (value == null || value <= 0 || !newValue.equals(newValue.trim())) {
                field.setText(oldValue);
                return;
            }
        }
        updateSaveButton();
    }
}
