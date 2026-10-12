package co.proxychecker.gui.controllers;

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

import co.proxychecker.components.Colors;
import co.proxychecker.components.Settings;
import co.proxychecker.components.UserSettings;
import co.proxychecker.components.entities.AnonymityColor;
import co.proxychecker.components.entities.ProxyAnonymity;
import co.proxychecker.components.entities.ProxyType;
import co.proxychecker.gui.AlertBox;

/**
 * Controller for Settings.fxml, the preferences window.
 */
public class SettingsController implements Initializable {

    @FXML
    private Button saveButton;

    @FXML
    private TextField threadsField;

    @FXML
    private TextField timeoutField;

    @FXML
    private ComboBox<String> proxyTypeCombo;

    @FXML
    private ColorPicker eliteColorPicker;

    @FXML
    private ColorPicker anonymousColorPicker;

    @FXML
    private ColorPicker transparentColorPicker;

    private final UserSettings settings = Settings.getConfig();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        for (ProxyType type : ProxyType.values()) {
            proxyTypeCombo.getItems().add(type.name());
        }

        // show current settings
        proxyTypeCombo.getSelectionModel().select(settings.getProxyType().name());
        timeoutField.setText(String.valueOf(settings.getTimeout()));
        threadsField.setText(String.valueOf(settings.getThreads()));
        initColor(eliteColorPicker, ProxyAnonymity.ELITE);
        initColor(anonymousColorPicker, ProxyAnonymity.ANONYMOUS);
        initColor(transparentColorPicker, ProxyAnonymity.TRANSPARENT);

        proxyTypeCombo.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> updateSaveButton());
        timeoutField.textProperty().addListener((observable, oldValue, newValue) ->
                restrictToPositiveInteger(timeoutField, oldValue, newValue));
        threadsField.textProperty().addListener((observable, oldValue, newValue) ->
                restrictToPositiveInteger(threadsField, oldValue, newValue));

        saveButton.setDisable(true); // nothing to save yet
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
     * Saves the proxy settings.
     */
    @FXML
    private void onSaveAction() {
        Integer threads = parse(threadsField.getText());
        Integer timeout = parse(timeoutField.getText());

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
            ProxyType type = ProxyType.valueOf(proxyTypeCombo.getSelectionModel().getSelectedItem());
            settings.setThreads(threads).setTimeout(timeout).setProxyType(type);
            if (Settings.saveConfig(settings)) {
                AlertBox.show(Alert.AlertType.INFORMATION, "Changes Saved",
                        "The changes made have been saved to disk!\n\n"
                                + "They will be used for the next proxy check.");
                saveButton.setDisable(true);
            } else {
                AlertBox.show(Alert.AlertType.ERROR, "Save Failed",
                        "Unable to save the configuration file onto disk!");
            }
        }
    }

    /**
     * Saves the color preferences.
     */
    @FXML
    private void onSaveColorsAction() {
        List<AnonymityColor> newScheme = new ArrayList<>();
        newScheme.add(new AnonymityColor(ProxyAnonymity.ELITE, toHex(eliteColorPicker.getValue())));
        newScheme.add(new AnonymityColor(ProxyAnonymity.ANONYMOUS, toHex(anonymousColorPicker.getValue())));
        newScheme.add(new AnonymityColor(ProxyAnonymity.TRANSPARENT, toHex(transparentColorPicker.getValue())));

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
        return !settings.getProxyType().name().equals(proxyTypeCombo.getSelectionModel().getSelectedItem())
                || !String.valueOf(settings.getTimeout()).equals(timeoutField.getText())
                || !String.valueOf(settings.getThreads()).equals(threadsField.getText());
    }

    private void updateSaveButton() {
        saveButton.setDisable(!settingsChanged());
    }

    /**
     * Makes sure the user is only able to enter a positive number in the text field
     */
    private void restrictToPositiveInteger(TextField field, String oldValue, String newValue) {
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
