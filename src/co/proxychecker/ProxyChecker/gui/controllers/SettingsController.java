package co.proxychecker.ProxyChecker.gui.controllers;

import java.net.URL;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import co.proxychecker.ProxyChecker.components.entities.ProxyAnonymity;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

import co.proxychecker.ProxyChecker.gui.AlertBox;
import co.proxychecker.ProxyChecker.components.Settings;
import co.proxychecker.ProxyChecker.components.UserSettings;
import javafx.scene.paint.Color;
import javafx.util.Pair;

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

    private UserSettings settings = Settings.getConfig();

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        //populate
        combo_type.getItems().add("HTTP");
        combo_type.getItems().add("SOCKS");

        // get current settings
        combo_type.getSelectionModel().select(settings.getProxyType().toString());
        field_timeout.setText(String.valueOf(settings.getTimeout()));
        field_threads.setText(String.valueOf(settings.getThreads()));
        for(Pair<ProxyAnonymity, String> p : settings.getColorScheme()) {
            switch (p.getKey()) {
                case ELITE:
                    color_elite.setValue(Color.web(p.getValue()));
                    break;
                case ANONYMOUS:
                    color_anonymous.setValue(Color.web(p.getValue()));
                    break;
                case TRANSPARENT:
                    color_transparent.setValue(Color.web(p.getValue()));
                    break;
            }
        }

        combo_type.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
                if(!settings.getProxyType().equals(newValue)) {
                    button_save.setDisable(false);
                }
                if(!settingsChanged()) {
                    button_save.setDisable(true);
                }
            }
        });

        field_timeout.textProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
                integerTextField(field_timeout, oldValue, newValue);
            }
        });
        field_threads.textProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
                integerTextField(field_threads, oldValue, newValue);
            }
        });


        button_save.setDisable(true); // disable save button by default
    }

    /**
     * Main button save handle
     */
    @FXML
    private void button_save_action() {
        Proxy.Type type = Proxy.Type.valueOf(combo_type.getSelectionModel().getSelectedItem());
        int threads = Integer.parseInt(field_threads.getText());
        int timeout = Integer.parseInt(field_timeout.getText());

        if(timeout < 500) { // restriction due to timeout value being too low will result
            // in null value for UserSettings.getIp() which will prevent application from working
            AlertBox.show(Alert.AlertType.ERROR, "Timeout Too Low",
                    "The minimum accepted timeout value for requests is 500 milliseconds.");
        } else {
            settings.setThreads(threads).setTimeout(timeout).setProxyType(type);
            if(Settings.saveConfig(settings)) {
                AlertBox.show(Alert.AlertType.INFORMATION, "Changes Saved",
                        "The changes made have been saved to disk!\n\n" +
                                "Restart the application for the changes to take effect.");
                button_save.setDisable(true);
            } else {
                AlertBox.show(Alert.AlertType.ERROR, "Save Failed",
                        "Unable to save the configuration file onto disk!");
            }
        }
    }

    @FXML
    private void button_save_color_action() {
        List<Pair<ProxyAnonymity, String>> newScheme = new ArrayList<>();
        newScheme.add(new Pair<>(ProxyAnonymity.ELITE,
                "#" + Integer.toHexString(color_elite.getValue().hashCode()).substring(0,6)));
        newScheme.add(new Pair<>(ProxyAnonymity.ANONYMOUS,
                "#" + Integer.toHexString(color_anonymous.getValue().hashCode()).substring(0,6)));
        newScheme.add(new Pair<>(ProxyAnonymity.TRANSPARENT,
                "#" + Integer.toHexString(color_transparent.getValue().hashCode()).substring(0,6)));

        settings.setColorScheme(newScheme);
        if(Settings.saveConfig(settings)) {
            AlertBox.show(Alert.AlertType.INFORMATION, "Changes Saved",
                    "The changes made have been saved to disk!");
            button_save.setDisable(true);
        } else {
            AlertBox.show(Alert.AlertType.ERROR, "Save Failed",
                    "Unable to save the configuration file onto disk!");
        }
    }

    /**
     * Determines whether or not if any proxy setting value has been changed
     * @return Boolean - Settings have been changed
     */
    private boolean settingsChanged() {
        return (
                (!(settings.getProxyType().toString().equals(combo_type.getSelectionModel().getSelectedItem()))) ||
                        (!(String.valueOf(settings.getTimeout()).equals(field_timeout.getText()))) ||
                        (!(String.valueOf(settings.getThreads()).equals(field_threads.getText())))
        );
    }

    /**
     * Makes sure the user is only able enter in a number on the textfield
     * @param field - TextField
     * @param oldValue - String the value before a new character is entered
     * @param newValue - String the value after the new character is entered
     */
    private void integerTextField(TextField field, String oldValue, String newValue) {
        try {
            if(field.getText().length() > 0 ) {
                int value = Integer.parseInt(newValue);
                if(field.getText().length() == 1) { // first numbered entered
                    if(value == 0) { // prevent from being a zero
                        field.setText(oldValue);
                    }
                }
            }
        } catch (NumberFormatException e) {
            field.setText(oldValue);
        }
        button_save.setDisable(!settingsChanged()); // only enable save button if values have changed
    }
}
