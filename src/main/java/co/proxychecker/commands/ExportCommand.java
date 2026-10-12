package co.proxychecker.commands;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javafx.scene.control.Alert;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;

import co.proxychecker.core.ProxyExporter;
import co.proxychecker.gui.AlertBox;
import co.proxychecker.components.entities.Proxy;
import co.proxychecker.components.entities.ProxyStatus;
import co.proxychecker.components.entities.ProxyAnonymity;

/**
 * Exports proxies to disk.
 */
public final class ExportCommand {

    private ExportCommand() {
    }

    /**
     * Asks for a destination file and saves all loaded proxies to it, one {@code ip:port} per line.
     */
    public static void save(ListView<String> listView) {
        if (listView.getItems().isEmpty()) {
            AlertBox.show(Alert.AlertType.INFORMATION, "No Proxies",
                    "There's currently no proxies loaded to export!");
            return;
        }
        File file = FileCommand.getFileToSave(false);
        if (file != null) {
            try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                for (String ipPort : listView.getItems()) {
                    writer.write(ipPort);
                    writer.write('\n');
                }
                AlertBox.show(Alert.AlertType.INFORMATION, "Proxies Exported",
                        "All proxies have been successfully exported to disk!");
            } catch (IOException e) {
                AlertBox.show(Alert.AlertType.ERROR, "Export Failed",
                        "Unable to export the data. Error: " + e.getMessage());
            }
        }
    }

    /**
     * Asks for a destination file and exports the whole result table to it as CSV.
     */
    public static void save(TableView<Proxy> tableView) {
        if (tableView.getItems().isEmpty()) {
            AlertBox.show(Alert.AlertType.INFORMATION, "No Data",
                    "There's currently no data to export!");
            return;
        }
        File file = FileCommand.getFileToSave(true);
        if (file != null) {
            try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                ProxyExporter.writeCsv(tableView.getItems(), writer);
                AlertBox.show(Alert.AlertType.INFORMATION, "Table Exported",
                        "The table has been successfully exported to disk!");
            } catch (IOException e) {
                AlertBox.show(Alert.AlertType.ERROR, "Export Failed",
                        "Unable to export the table. Error: " + e.getMessage());
            }
        }
    }

    /**
     * Asks for a destination file and saves the checked proxies matching the given status and
     * anonymity to it, one {@code ip:port} per line.
     *
     * @param proxyAnonymity required anonymity, or null for any anonymity
     */
    public static void save(TableView<Proxy> tableView, ProxyStatus proxyStatus, ProxyAnonymity proxyAnonymity) {
        if (tableView.getItems().isEmpty()) {
            AlertBox.show(Alert.AlertType.INFORMATION, "No Data",
                    "There's currently no data to export!");
            return;
        }
        File file = FileCommand.getFileToSave(false);
        if (file != null) {
            try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                for (Proxy proxy : tableView.getItems()) {
                    if (ProxyExporter.matches(proxy, proxyStatus, proxyAnonymity)) {
                        writer.write(proxy.getSpec()); // as loaded, keeping credentials
                        writer.write('\n');
                    }
                }
                AlertBox.show(Alert.AlertType.INFORMATION, "Data Exported",
                        "The data has been successfully exported to disk!");
            } catch (IOException e) {
                AlertBox.show(Alert.AlertType.ERROR, "Export Failed",
                        "Unable to export the data. Error: " + e.getMessage());
            }
        }
    }
}
