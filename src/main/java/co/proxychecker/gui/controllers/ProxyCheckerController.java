package co.proxychecker.gui.controllers;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.TransferMode;

import co.proxychecker.commands.ExportCommand;
import co.proxychecker.commands.LoadCommand;
import co.proxychecker.commands.ProxyCheckCommand;
import co.proxychecker.components.Settings;
import co.proxychecker.components.entities.Proxy;
import co.proxychecker.components.entities.ProxyAnonymity;
import co.proxychecker.components.entities.ProxyStatus;
import co.proxychecker.gui.AlertBox;
import co.proxychecker.gui.Resources;
import co.proxychecker.gui.Window;
import co.proxychecker.gui.events.ProxyCheckerKeyEvent;

/**
 * Controller for ProxyChecker.fxml, the main window.
 */
public class ProxyCheckerController implements Initializable {

    @FXML
    private TextField inputField;
    @FXML
    private Button checkButton;

    @FXML
    private TableView<Proxy> proxyTable;
    @FXML
    private ListView<String> loadedProxiesList;

    @FXML
    private TableColumn<Proxy, String> ipColumn;
    @FXML
    private TableColumn<Proxy, Integer> portColumn;
    @FXML
    private TableColumn<Proxy, ProxyStatus> statusColumn;
    @FXML
    private TableColumn<Proxy, String> countryColumn;
    @FXML
    private TableColumn<Proxy, ProxyAnonymity> anonymityColumn;
    @FXML
    private TableColumn<Proxy, String> responseTimeColumn;

    @FXML
    private Label loadedProxiesLabel;
    @FXML
    private Label checkedProxiesLabel;
    @FXML
    private Label workingProxiesLabel;
    @FXML
    private Label ipAddressLabel;

    @FXML
    private ProgressBar progressBar;

    @FXML
    private MenuItem openFileItem;
    @FXML
    private MenuItem preferencesItem;

    private int totalToCheck;
    private int workingProxies;

    @Override
    public void initialize(URL location, ResourceBundle resourceBundle) {
        ipAddressLabel.setText(Settings.getConfig().getIp());

        ProxyCheckerKeyEvent keyHandler = new ProxyCheckerKeyEvent();
        loadedProxiesList.setOnKeyPressed(keyHandler);
        proxyTable.setOnKeyPressed(keyHandler);

        ipColumn.setCellValueFactory(new PropertyValueFactory<>("ip"));
        portColumn.setCellValueFactory(new PropertyValueFactory<>("port"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("proxyStatus"));
        anonymityColumn.setCellValueFactory(new PropertyValueFactory<>("proxyAnonymity"));
        countryColumn.setCellValueFactory(new PropertyValueFactory<>("country"));
        responseTimeColumn.setCellValueFactory(new PropertyValueFactory<>("responseTime"));

        // highlight working proxies using the configured anonymity colors
        proxyTable.setRowFactory(table -> new TableRow<Proxy>() {
            @Override
            protected void updateItem(Proxy proxy, boolean empty) {
                super.updateItem(proxy, empty);
                String color = (empty || proxy == null || proxy.getProxyStatus() == ProxyStatus.DEAD)
                        ? null
                        : Settings.getConfig().getColor(proxy.getProxyAnonymity());
                setStyle(color == null ? "" : "-fx-background-color: " + color + ";");
            }
        });

        // allow drag and drop of files into the loaded proxies list
        loadedProxiesList.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles() && !ProxyCheckCommand.isRunning()) {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
            event.consume();
        });
        loadedProxiesList.setOnDragDropped(event -> {
            boolean loaded = false;
            if (event.getDragboard().hasFiles() && !ProxyCheckCommand.isRunning()) {
                LoadCommand.file(loadedProxiesList, event.getDragboard().getFiles());
                loaded = true;
            }
            event.setDropCompleted(loaded);
            event.consume();
        });

        loadedProxiesList.getItems().addListener((ListChangeListener<String>) change ->
                loadedProxiesLabel.setText("Loaded Proxies: " + loadedProxiesList.getItems().size()));
    }

    /**
     * Executed when the enter key is pressed in the input field.
     */
    @FXML
    private void onInputAction() {
        String input = inputField.getText();
        if (!input.isEmpty() && !ProxyCheckCommand.isRunning()) {
            LoadCommand.string(input, loadedProxiesList);
        }
        inputField.clear();
    }

    /**
     * Executed when the check button is clicked: starts a check, or stops the running one.
     */
    @FXML
    private void onCheckAction() {
        if (ProxyCheckCommand.isRunning()) {
            ProxyCheckCommand.stop();
            checkButton.setText("Stopping…");
            checkButton.setDisable(true); // re-enabled once the check has wound down
            return;
        }
        if (loadedProxiesList.getItems().isEmpty()) {
            AlertBox.show(Alert.AlertType.INFORMATION, "No Loaded Proxies",
                    "There are no proxies to check! To check a proxy you must load at least one first.");
            return;
        }
        totalToCheck = loadedProxiesList.getItems().size();
        workingProxies = 0;
        progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        workingProxiesLabel.setText("Working Proxies: 0");
        checkedProxiesLabel.setText("Checked Proxies: 0");
        proxyTable.getItems().clear();
        setChecking(true);
        if (!ProxyCheckCommand.check(new ArrayList<>(loadedProxiesList.getItems()),
                this::onProxiesChecked, this::onCheckCompleted)) {
            setChecking(false);
        }
    }

    /**
     * Switches the window between idle and checking: while checking, the Check button becomes Stop and
     * everything that would change the proxy list or the settings in use is disabled.
     */
    private void setChecking(boolean checking) {
        checkButton.setText(checking ? "Stop" : "Check");
        checkButton.setDisable(false);
        inputField.setDisable(checking);
        openFileItem.setDisable(checking);
        preferencesItem.setDisable(checking);
        loadedProxiesList.setDisable(checking); // also blocks Delete and drag and drop
    }

    /**
     * Adds checked proxies to the table and updates the counters and progress bar.
     */
    private void onProxiesChecked(List<Proxy> proxies) {
        proxyTable.getItems().addAll(proxies);
        for (Proxy proxy : proxies) {
            if (proxy.getProxyStatus() == ProxyStatus.ALIVE) {
                workingProxies++;
            }
        }
        int checked = proxyTable.getItems().size();
        checkedProxiesLabel.setText("Checked Proxies: " + checked);
        workingProxiesLabel.setText("Working Proxies: " + workingProxies);
        progressBar.setProgress((double) checked / totalToCheck);
    }

    private void onCheckCompleted(boolean stopped) {
        setChecking(false);
        if (stopped) {
            int checked = proxyTable.getItems().size();
            progressBar.setProgress((double) checked / totalToCheck);
            checkedProxiesLabel.setText("Checked Proxies: " + checked + " of " + totalToCheck + " (stopped, "
                    + (totalToCheck - checked) + " not checked)");
            return;
        }
        progressBar.setProgress(0);
        AlertBox.show(Alert.AlertType.INFORMATION, "Task Completed",
                "Proxy Checker has finished checking your proxies!");
    }

    /**
     * Handles clicks on the menu items, identified by their id.
     */
    @FXML
    private void onMenuItemAction(ActionEvent event) {
        if (!(event.getSource() instanceof MenuItem)) {
            return;
        }
        String itemId = ((MenuItem) event.getSource()).getId();
        switch (itemId) {
            case "open_file":
                if (!ProxyCheckCommand.isRunning()) {
                    LoadCommand.file(loadedProxiesList, null);
                }
                break;
            case "export_all":
                ExportCommand.save(loadedProxiesList);
                break;
            case "export_all_alive":
                ExportCommand.save(proxyTable, ProxyStatus.ALIVE, null);
                break;
            case "export_alive_elite":
                ExportCommand.save(proxyTable, ProxyStatus.ALIVE, ProxyAnonymity.ELITE);
                break;
            case "export_alive_anonymous":
                ExportCommand.save(proxyTable, ProxyStatus.ALIVE, ProxyAnonymity.ANONYMOUS);
                break;
            case "export_alive_transparent":
                ExportCommand.save(proxyTable, ProxyStatus.ALIVE, ProxyAnonymity.TRANSPARENT);
                break;
            case "export_all_dead":
                ExportCommand.save(proxyTable, ProxyStatus.DEAD, null);
                break;
            case "export_table":
                ExportCommand.save(proxyTable);
                break;
            case "preferences":
                Window.show("Preferences", Resources.SETTINGS_VIEW);
                break;
            case "about":
                Window.show("About", Resources.ABOUT_VIEW);
                break;
            case "quit":
                ProxyCheckCommand.stop();
                System.exit(0);
                break;
            default:
                break;
        }
    }
}
