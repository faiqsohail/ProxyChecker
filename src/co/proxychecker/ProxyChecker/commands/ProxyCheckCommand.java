package co.proxychecker.ProxyChecker.commands;

import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import javafx.util.Pair;
import javafx.scene.control.*;
import javafx.application.Platform;

import co.proxychecker.ProxyChecker.components.Settings;
import co.proxychecker.ProxyChecker.components.RequestAPI;
import co.proxychecker.ProxyChecker.components.UserSettings;
import co.proxychecker.ProxyChecker.components.entities.Proxy;
import co.proxychecker.ProxyChecker.components.entities.ProxyStatus;

/**
 * Takes each proxy value in the ListView, performs checks and updates TableView accordingly.
 */
public class ProxyCheckCommand {

    private static int thCount_start = Thread.activeCount();
    private static UserSettings settings = Settings.getConfig();

    /**
     * Setups the thread pool and launches asynchronous checks on the list of proxies
     * @param listView - The ListView containing the proxies that will be checked
     * @param tableView - The TableView that will be updated with the status of the proxies
     */
    public static void check(ListView<String> listView, TableView<Proxy> tableView) {
        ExecutorService executorService = Executors.newFixedThreadPool(settings.getThreads());
        for (String proxy : listView.getItems()) {
            executorService.submit(
                    new Checker(
                            new Proxy( // first creation of the Proxy object from the ListView
                                    proxy,
                                    settings.getProxyType()
                            ),
                            tableView
                    )
            );
        }

        executorService.shutdown();
    }

    /**
     * @return Boolean - Whether or not if proxies are still being checked
     */
    public static boolean isRunning() {
        return (Thread.activeCount() - thCount_start) != 0;
    }

    /**
     * Task that asynchronous checks each proxy and updates the TableView
     */
    private static class Checker implements Runnable {

        private Proxy proxy;
        private TableView<Proxy> tableView;

        /**
         *
         * @param proxy - The Proxy object to check.
         * @param tableView
         */
        public Checker(Proxy proxy, TableView<Proxy> tableView) {
            this.proxy = proxy;
            this.tableView = tableView;
        }

        @Override
        public void run() {
            RequestAPI requestAPI = new RequestAPI(settings);
            java.net.Proxy proxy = new java.net.Proxy(
                    this.proxy.getProxyType(),
                    new InetSocketAddress(
                            this.proxy.getIp(),
                            this.proxy.getPort()
                    )
            );

            Pair<HttpURLConnection, Long> pair = requestAPI.connect(proxy);
            if(pair != null) {
                try {
                    this.proxy.setProxyStatus(ProxyStatus.ALIVE);
                    RequestAPI.Response response = requestAPI.getResponse(pair.getKey());
                    this.proxy.setProxyAnonymity(response.anonymity);
                    this.proxy.setCountry(response.country);
                    this.proxy.setResponseTime(pair.getValue() + " (ms)");

                } catch (Exception e) {
                    this.proxy.setProxyStatus(ProxyStatus.DEAD);
                    this.proxy.setProxyAnonymity(null);
                }
            } else {
                this.proxy.setProxyStatus(ProxyStatus.DEAD);
                this.proxy.setProxyAnonymity(null);
            }

            // this has to be done on another thread
            Platform.runLater(()-> {
                tableView.getItems().add(this.proxy);
            });
        }
    }
}
