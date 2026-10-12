package co.proxychecker.gui.controllers;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import co.proxychecker.components.Settings;
import co.proxychecker.gui.Browser;

/**
 * Controller for About.fxml.
 */
public class AboutController implements Initializable {

    @FXML
    private Text name;
    @FXML
    private Hyperlink hyperlink;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        name.setText(Settings.getApplicationName() + " v." + Settings.getApplicationVersion());
        hyperlink.setText(Settings.getApplicationUrl().replaceFirst("^https?://", ""));
        hyperlink.setOnAction(event -> {
            Browser.open(Settings.getApplicationUrl());
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.close();
        });
    }
}
