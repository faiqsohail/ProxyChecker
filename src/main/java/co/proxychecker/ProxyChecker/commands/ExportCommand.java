package co.proxychecker.ProxyChecker.commands;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

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

    /**
     * Gets a user selected destination file to save all loaded proxies onto, in the format ip:port
     * @param listView - collection component
     */
    public static void save(ListView<String> listView) {
        if (listView.getItems().size() != 0) {
            File file = FileCommand.getFileToSave(false);
            if (file != null) {
                try {
                    PrintWriter printWriter = new PrintWriter(new FileWriter(file));
                    for (String ip_port : listView.getItems()) {
                        printWriter.write(ip_port + "\n");
                    }
                    printWriter.close();
                    AlertBox.show(Alert.AlertType.INFORMATION, "Proxies Exported",
                            "All proxies have been successfully exported to disk!");
                } catch (IOException e) {
                    AlertBox.show(Alert.AlertType.ERROR, "Export Failed",
                            "Unable to export the data. Error: " + e.getMessage());
                }
            }
        } else {
            AlertBox.show(Alert.AlertType.INFORMATION, "No Proxies",
                    "There's currently no proxies loaded to export!");
        }
    }

    /**
     * Gets a user selected destination file to export the proxy table in a comma separated value file.
     * @param tableView - collection component
     */
    public static void save(TableView<Proxy> tableView) {
        if (tableView.getItems().size() != 0) {
            File file = FileCommand.getFileToSave(true);
            if (file != null) {
                try {
                    PrintWriter printWriter = new PrintWriter(new FileWriter(file));
                    for (Proxy proxy : tableView.getItems()) {
                        printWriter.write(getCSV(proxy));
                    }
                    printWriter.close();
                    AlertBox.show(Alert.AlertType.INFORMATION, "Table Exported",
                            "The table has been successfully exported to disk!");
                } catch (IOException e) {
                    AlertBox.show(Alert.AlertType.ERROR, "Export Failed",
                            "Unable to export the table. Error: " + e.getMessage());
                }
            }
        } else {
            AlertBox.show(Alert.AlertType.INFORMATION, "No Data",
                    "There's currently no data to export!");
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
        if (tableView.getItems().size() != 0) {
            File file = FileCommand.getFileToSave(false);
            if (file != null) {
                try {
                    PrintWriter printWriter = new PrintWriter(new FileWriter(file));
                    for (Proxy proxy : tableView.getItems()) {

                        String line = getLine(proxy, proxyStatus, proxyAnonymity);
                        if (line != null) { // a valid line was given
                            printWriter.write(line);
                        }
                    }
                    printWriter.close();
                    AlertBox.show(Alert.AlertType.INFORMATION, "Data Exported",
                            "The data has been successfully exported to disk!");
                } catch (IOException e) {
                    AlertBox.show(Alert.AlertType.ERROR, "Export Failed",
                            "Unable to export the data. Error: " + e.getMessage());
                }
            }
        } else {
            AlertBox.show(Alert.AlertType.INFORMATION, "No Data",
                    "There's currently no data to export!");
        }
    }

    /**
     * Generates comma separated values (csv) for a given Proxy
     * @param proxy - The Proxy object
     * @return String - csv
     */
    private static String getCSV(Proxy proxy) {
        StringBuilder sb = new StringBuilder();
        sb.append(proxy.getIp() + ",");
        sb.append(proxy.getPort() + ",");
        sb.append(proxy.getProxyStatus() + ",");
        sb.append(proxy.getProxyAnonymity() + ",");
        sb.append(proxy.getCountry() + ",");
        sb.append(proxy.getResponseTime() + "\n");

        return sb.toString();
    }

    /**
     * Generates a line in the form ip:port for a given proxy confining to ProxyStatus and ProxyAnonymity
     * @param proxy - The Proxy object
     * @param proxyStatus - The ProxyStatus
     * @param proxyAnonymity - The ProxyAnonymity (null for all proxies belonging to ProxyStatus)
     * @return String - in the form ip:port,
     *         null if the proxy does not confine to the given proxyStatus and/or proxyAnonymity
     */
    private static String getLine(Proxy proxy, ProxyStatus proxyStatus, ProxyAnonymity proxyAnonymity) {
        if (proxy.getProxyStatus() == proxyStatus) {
            if (proxyAnonymity != null) {
                if (proxyAnonymity == proxy.getProxyAnonymity()) {
                    return proxy.getIp() + ":" + proxy.getPort() + "\n";
                } else {
                    return null;
                }
            } else {
                return proxy.getIp() + ":" + proxy.getPort() + "\n";
            }
        }
        return null;
    }
}
