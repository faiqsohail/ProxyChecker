package co.proxychecker.ProxyChecker.gui.controllers;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.input.DragEvent;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.input.TransferMode;
import javafx.collections.ListChangeListener;
import javafx.scene.control.cell.PropertyValueFactory;


import co.proxychecker.ProxyChecker.gui.Window;
import co.proxychecker.ProxyChecker.gui.AlertBox;
import co.proxychecker.ProxyChecker.components.Settings;
import co.proxychecker.ProxyChecker.commands.LoadCommand;
import co.proxychecker.ProxyChecker.commands.ExportCommand;
import co.proxychecker.ProxyChecker.components.entities.Proxy;
import co.proxychecker.ProxyChecker.commands.ProxyCheckCommand;
import co.proxychecker.ProxyChecker.components.entities.ProxyStatus;
import co.proxychecker.ProxyChecker.gui.events.ProxyCheckerKeyEvent;
import co.proxychecker.ProxyChecker.components.entities.ProxyAnonymity;
import javafx.scene.paint.Color;
import javafx.util.Callback;
import javafx.util.Pair;

/**
 * Controller for ProxyChecker.fxml
 */
public class ProxyCheckerController implements Initializable {

    @FXML
    private TextField field_input;
    @FXML
    private Button button_check;

    @FXML
    private TableView<Proxy> table_proxy = new TableView<>();
    @FXML
    private ListView<String> view_loaded_proxies = new ListView<>();

    @FXML
    public TableColumn<Proxy, String> column_ip;
    @FXML
    public TableColumn<Proxy, Integer> column_port;
    @FXML
    public TableColumn<Proxy, String> column_status;
    @FXML
    public TableColumn<Proxy, String> column_country;
    @FXML
    public TableColumn<Proxy, String> column_anonymity;
    @FXML
    public TableColumn<Proxy, String> column_response_time;


    @FXML
    private Label label_loaded_proxies;
    @FXML
    private Label label_checked_proxies;
    @FXML
    private Label label_working_proxies;
    @FXML
    private Label label_ip_address;

    @FXML
    private ProgressBar progressBar;

    private int totalToCheck;
    private int workingProxies;


    @Override
    public void initialize(URL location, ResourceBundle resourceBundle) {
        // set users IP address on label
        label_ip_address.setText(Settings.getConfig().getIp());

        // setup key press event handler
        view_loaded_proxies.setOnKeyPressed(new ProxyCheckerKeyEvent());
        table_proxy.setOnKeyPressed(new ProxyCheckerKeyEvent());

        // setup table factory
        column_ip.setCellValueFactory(new PropertyValueFactory<>("Ip"));
        column_port.setCellValueFactory(new PropertyValueFactory<>("Port"));
        column_status.setCellValueFactory(new PropertyValueFactory<>("ProxyStatus"));
        column_anonymity.setCellValueFactory(new PropertyValueFactory<>("ProxyAnonymity"));
        column_country.setCellValueFactory(new PropertyValueFactory<>("Country"));
        column_response_time.setCellValueFactory(new PropertyValueFactory<>("ResponseTime"));

        table_proxy.setRowFactory(tp -> new TableRow<Proxy>() {
            @Override
            protected void updateItem(Proxy proxy, boolean empty) {
                super.updateItem(proxy, empty);
                if ((proxy == null) || (proxy.getProxyStatus() == ProxyStatus.DEAD))  {
                    setStyle("");
                } else {
                    String color = Settings.getConfig().getColor(proxy.getProxyAnonymity());
                    setStyle(color == null ? "" : "-fx-background-color: " + color + ";");
                }
            }
        });

        // allow drag and drop of files into the loaded proxies view
        view_loaded_proxies.setOnDragOver(new EventHandler<DragEvent>() {
            @Override
            public void handle(DragEvent event) {
                if(!ProxyCheckCommand.isRunning()) {
                    event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
                } else {
                    event.acceptTransferModes(TransferMode.NONE);
                }
            }
        });

        // route the files dropped to the proper load command
        view_loaded_proxies.setOnDragDropped(new EventHandler<DragEvent>() {
            @Override
            public void handle(DragEvent event) {
                boolean loaded = false;
                if(event.getDragboard().hasFiles() && !ProxyCheckCommand.isRunning()) {
                    LoadCommand.file(view_loaded_proxies, event.getDragboard().getFiles());
                    loaded = true;
                }
                event.setDropCompleted(loaded);
                event.consume();
            }
        });

        // update loaded proxy count
        view_loaded_proxies.getItems().addListener(new ListChangeListener<String>() {
            @Override
            public void onChanged(Change<? extends String> c) {
                label_loaded_proxies.setText("Loaded Proxies: " + String.valueOf(view_loaded_proxies.getItems().size()));
            }
        });

    }

    /**
     * Executed when enter key is pressed on field_input
     */
    @FXML
    private void field_input_action() {
        String input = field_input.getText();
        if(!input.isEmpty() && (!ProxyCheckCommand.isRunning())) {
            LoadCommand.string(input, view_loaded_proxies);
        }
        field_input.clear();
    }

    /**
     * Executed when button_check is clicked
     */
    @FXML
    private void button_check_action() {
        if (ProxyCheckCommand.isRunning()) {
            return;
        }
        if (view_loaded_proxies.getItems().isEmpty()) { // make sure there are proxies loaded to check
            AlertBox.show(Alert.AlertType.INFORMATION, "No Loaded Proxies",
                    "There are no proxies to check! To check a proxy you must load at least one first.");
            return;
        }
        totalToCheck = view_loaded_proxies.getItems().size();
        workingProxies = 0;
        progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        label_working_proxies.setText("Working Proxies: 0");
        label_checked_proxies.setText("Checked Proxies: 0");
        table_proxy.getItems().clear(); // reset table
        field_input.requestFocus();
        button_check.setDisable(true);
        ProxyCheckCommand.check(new ArrayList<>(view_loaded_proxies.getItems()),
                this::onProxiesChecked, this::onCheckCompleted);
    }

    /**
     * Adds checked proxies to the table and updates the counters and progress bar
     */
    private void onProxiesChecked(List<Proxy> proxies) {
        table_proxy.getItems().addAll(proxies);
        for (Proxy proxy : proxies) {
            if (proxy.getProxyStatus() == ProxyStatus.ALIVE) {
                workingProxies++;
            }
        }
        int checked = table_proxy.getItems().size();
        label_checked_proxies.setText("Checked Proxies: " + checked);
        label_working_proxies.setText("Working Proxies: " + workingProxies);
        progressBar.setProgress((double) checked / totalToCheck);
    }

    private void onCheckCompleted() {
        progressBar.setProgress(0);
        button_check.setDisable(false);
        AlertBox.show(Alert.AlertType.INFORMATION, "Task Completed",
                "Proxy Checker has finished checking your proxies!");
    }


    /**
     * Manages what to do when a menu item is clicked
     * @param e - ActionEvent
     */
    @FXML
    private void MenuItemHandler(ActionEvent e) {
        if(e.getSource() instanceof MenuItem) {
            MenuItem item = (MenuItem)e.getSource();
            String item_id = item.getId();
            switch (item_id) {
                case "open_file":
                    if(!ProxyCheckCommand.isRunning()) {
                        LoadCommand.file(view_loaded_proxies, null);
                    }
                    break;
                case "export_all":
                    ExportCommand.save(view_loaded_proxies);
                    break;
                case "export_all_alive":
                    ExportCommand.save(table_proxy, ProxyStatus.ALIVE, null);
                    break;
                case "export_alive_elite":
                    ExportCommand.save(table_proxy, ProxyStatus.ALIVE, ProxyAnonymity.ELITE);
                    break;
                case "export_alive_anonymous":
                    ExportCommand.save(table_proxy, ProxyStatus.ALIVE, ProxyAnonymity.ANONYMOUS);
                    break;
                case "export_alive_transparent":
                    ExportCommand.save(table_proxy, ProxyStatus.ALIVE, ProxyAnonymity.TRANSPARENT);
                    break;
                case "export_all_dead":
                    ExportCommand.save(table_proxy, ProxyStatus.DEAD, null);
                    break;
                case "export_table":
                    ExportCommand.save(table_proxy);
                    break;
                case "preferences":
                    Window.show(item_id.substring(0, 1).toUpperCase() + item_id.substring(1),
                            new FXMLLoader(
                                    getClass().getResource("/co/proxychecker/ProxyChecker/gui/Settings.fxml")
                            ));
                    break;
                case "about":
                    Window.show(item_id.substring(0, 1).toUpperCase() + item_id.substring(1),
                            new FXMLLoader(
                                    getClass().getResource("/co/proxychecker/ProxyChecker/gui/About.fxml")
                            ));
                    break;
                case "quit":
                    System.exit(0);
                    break;

            }
        }
    }

}
