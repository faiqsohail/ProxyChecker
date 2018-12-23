package co.proxychecker.ProxyChecker.startup;

import java.io.IOException;

import javax.imageio.ImageIO;
import com.sun.javafx.PlatformUtil;

import javafx.scene.Scene;
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

        if (PlatformUtil.isMac()) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            com.apple.eawt.Application.getApplication().setDockIconImage(
                    ImageIO.read(
                            getClass().getResourceAsStream("/co/proxychecker/ProxyChecker/assets/icon.png")
                    )
            );
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
                    getClass().getResourceAsStream("/co/proxychecker/ProxyChecker/gui/ProxyChecker.fxml")
            );
            Scene scene = new Scene(root);
            primaryStage.getIcons().add(new Image(
                    getClass().getResourceAsStream("/co/proxychecker/ProxyChecker/assets/icon.png"))
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
