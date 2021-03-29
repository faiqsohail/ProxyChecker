package co.proxychecker.ProxyChecker.startup;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Objects;

import javax.imageio.ImageIO;

import co.proxychecker.ProxyChecker.components.UpdateChecker;
import com.sun.javafx.PlatformUtil;

import javafx.scene.Scene;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import javafx.fxml.FXMLLoader;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javafx.stage.WindowEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Alert;
import javafx.application.Application;

import co.proxychecker.ProxyChecker.gui.AlertBox;
import co.proxychecker.ProxyChecker.components.Settings;
import co.proxychecker.ProxyChecker.components.RequestAPI;
import co.proxychecker.ProxyChecker.components.UserSettings;


public class ProxyChecker extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {

        UpdateChecker checker = new UpdateChecker();
        if(checker.isUpdateAvailable()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setHeaderText("Update");
            alert.setTitle("A new update is available!");
            alert.setContentText("A new update for " + Settings.getApplicationName() +
                    " is available would you like to download it now? \n\n" +
                    "Current Version: " + Settings.getApplicationVersion() + "\n" +
                    "New Version: " + checker.getRepoVersion());
            alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
            alert.showAndWait().ifPresent(type -> {
                if (type == ButtonType.YES) {
                    try {
                        Desktop.getDesktop().browse(new URL(Settings.getApplicationUrl()).toURI());
                        System.exit(0);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    Alert alert2 = new Alert(Alert.AlertType.WARNING);
                    alert2.setHeaderText("Notice");
                    alert2.setContentText("Support is not provided for outdated versions of the application.\n\n" +
                            "Outdated version(s) of the application may cease to work at anytime. ");
                    alert2.showAndWait();
                }
            });

        }

        try {
            UserSettings settings = Settings.getConfig();
            RequestAPI requestAPI = new RequestAPI(settings);
            String ip = requestAPI.getResponse(requestAPI.connect(null).getKey()).ip;

            if(!settings.getIp().equals(ip)) {
                Settings.saveConfig(settings.setIp(ip));
            }

            FXMLLoader fxmlLoader = new FXMLLoader();
            VBox root = fxmlLoader.load(
//                    getClass().getResourceAsStream("/co/proxychecker/ProxyChecker/gui/ProxyChecker.fxml")
//                    new FileInputStream( new File("C:\\Users\\User\\IdeaProjects\\ProxyChecker_gui\\src\\main\\java\\co\\proxychecker\\ProxyChecker\\gui\\ProxyChecker.fxml").getAbsolutePath())
                    new FileInputStream( new File("C:\\Users\\User\\IdeaProjects\\ProxyChecker_gui\\src\\main\\java\\co\\proxychecker\\ProxyChecker\\gui\\ProxyChecker.fxml").getAbsolutePath())
            );
            Scene scene = new Scene(root);
            primaryStage.getIcons().add(new Image(
                    new FileInputStream( new File("C:\\Users\\User\\IdeaProjects\\ProxyChecker_gui\\src\\main\\java\\co\\proxychecker\\ProxyChecker\\assets\\icon.png").getAbsolutePath()))
            );
            primaryStage.setScene(scene);
            primaryStage.setTitle(Settings.getApplicationName());
            primaryStage.show();

        } catch (IOException | NullPointerException e) {
            AlertBox.show(
                    Alert.AlertType.ERROR,
                    "Service Down",
                    "Unable to get current public IP address, " +
                            "will not be able to determine proxy status and anonymity!\n\n" +
                            "Check your internet connection and try again later, you may also visit " +
                            Settings.getApplicationUrl() + " for support."
            );
        }

        primaryStage.setOnCloseRequest(new EventHandler<WindowEvent>() {
            @Override
            public void handle(WindowEvent event) {
                System.exit(0);
            }
        });
    }
}
