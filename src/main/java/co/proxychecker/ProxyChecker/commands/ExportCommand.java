package co.proxychecker.ProxyChecker.commands;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import javafx.scene.control.Alert;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;

import co.proxychecker.ProxyChecker.gui.AlertBox;
import co.proxychecker.ProxyChecker.components.entities.Proxy;
import co.proxychecker.ProxyChecker.components.entities.ProxyStatus;
import co.proxychecker.ProxyChecker.components.entities.ProxyAnonymity;

/**
 * Class contains static methods that facilitate exporting a Proxies to disk.
 */
public class ExportCommand {

    static final String CSV_HEADER = "IP,Port,Status,Anonymity,Country,Response Time";

    /**
     * Gets a user selected destination file to save all loaded proxies onto, in the format ip:port
     * @param listView - collection component
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
     * Gets a user selected destination file to export the proxy table in a comma separated value file.
     * @param tableView - collection component
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
                writeCsv(tableView.getItems(), writer);
                AlertBox.show(Alert.AlertType.INFORMATION, "Table Exported",
                        "The table has been successfully exported to disk!");
            } catch (IOException e) {
                AlertBox.show(Alert.AlertType.ERROR, "Export Failed",
                        "Unable to export the table. Error: " + e.getMessage());
            }
        }
    }

    /**
     * Gets a user selected destination file to export the proxy table based on the given ProxyStatus and
     * ProxyAnonymity values, in the format ip:port
     * @param tableView - collection component
     * @param proxyStatus - The ProxyStatus
     * @param proxyAnonymity - The ProxyAnonymity (null for all proxies belonging to ProxyStatus)
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
                    if (matches(proxy, proxyStatus, proxyAnonymity)) {
                        writer.write(proxy.getIp() + ":" + proxy.getPort());
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

    /**
     * Writes the proxies as CSV (RFC 4180) with a header row.
     */
    static void writeCsv(List<Proxy> proxies, Appendable out) throws IOException {
        out.append(CSV_HEADER).append('\n');
        for (Proxy proxy : proxies) {
            out.append(csvField(proxy.getIp())).append(',')
                    .append(String.valueOf(proxy.getPort())).append(',')
                    .append(csvField(proxy.getProxyStatus())).append(',')
                    .append(csvField(proxy.getProxyAnonymity())).append(',')
                    .append(csvField(proxy.getCountry())).append(',')
                    .append(csvField(proxy.getResponseTime())).append('\n');
        }
    }

    /**
     * Quotes a CSV field when needed, e.g. country names such as "Korea, Republic of". Null becomes empty.
     */
    static String csvField(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }

    static boolean matches(Proxy proxy, ProxyStatus proxyStatus, ProxyAnonymity proxyAnonymity) {
        return proxy.getProxyStatus() == proxyStatus
                && (proxyAnonymity == null || proxyAnonymity == proxy.getProxyAnonymity());
    }
}
