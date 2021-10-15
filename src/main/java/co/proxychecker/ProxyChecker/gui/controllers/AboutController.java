package co.proxychecker.ProxyChecker.gui.controllers;

import java.awt.*;
import java.net.URL;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.stage.Stage;
import javafx.scene.text.Text;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.Initializable;
import javafx.scene.control.Hyperlink;


import co.proxychecker.ProxyChecker.components.Settings;

/**
 * Controller for About.fxml
 */
public class AboutController implements Initializable {

    @FXML
    private Text name;
    @FXML
    private Hyperlink hyperlink;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        name.setText(Settings.getApplicationName() + " v." + Settings.getApplicationVersion());
        hyperlink.setText(Settings.getApplicationUrl().substring(8));
        hyperlink.setOnAction(new EventHandler<ActionEvent>() { 
            @Override
            public void handle(ActionEvent event) {
                try {
                    Desktop.getDesktop().browse(new URL(Settings.getApplicationUrl()).toURI());
                    Node source = (Node) event.getSource();
                    Stage stage = (Stage) source.getScene().getWindow();
                    stage.close();
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
            }
        });
    }
}
